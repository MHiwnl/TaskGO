package com.taskgo.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pagamento em dinheiro.
 * - O cliente entrega o valor em espécie a quem atendeu, no fim do serviço; o dinheiro não passa pela carteira.
 * - Sem troco em espécie: troco online, que sai do saldo de quem atendeu e vai para a carteira do cliente.
 * - A taxa da TaskGO vira pendência de quem atendeu: sai do saldo na hora, dos próximos repasses ou no balanço semanal.
 * - O cliente troca a forma de pagamento uma vez (dinheiro para cartão, Pix ou carteira, e o contrário).
 * - Cliente não pagou: quem atendeu cancela; o serviço (ou a taxa de cancelamento, se nem começou) vira pendência
 *   do cliente, que fica bloqueado de fazer pedidos até pagar com cartão ou com a carteira.
 */
@Service
public class DinheiroService {
    public static final String FORMA = "formaPagamento", DINHEIRO = "dinheiro", ONLINE = "online", PIX_DIRETO = "pix_direto", MAQUININHA = "cartao_maquininha";
    static final java.util.Set<String> FORMAS_DIRETAS = java.util.Set.of(DINHEIRO, PIX_DIRETO, MAQUININHA);
    static final java.util.Set<String> DO_CLIENTE = java.util.Set.of(Pendencia.SERVICO_NAO_PAGO, Pendencia.TAXA_CANCELAMENTO);

    private final PendenciaRepository pendencias;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final CarteiraRepository carteiras;
    private final TransacaoFinanceiraRepository transacoes;
    private final PagamentoService pagamentos;
    private final EventosService eventos;
    private final BigDecimal taxaCancelamento, cancelamentoMinimo, cancelamentoMaximo;
    private NotificacaoService notificacoes;
    private ModeracaoService moderacao;

    public DinheiroService(PendenciaRepository pendencias, PedidoContratadoRepository pedidos, UsuarioRepository usuarios, CarteiraRepository carteiras,
                           TransacaoFinanceiraRepository transacoes, PagamentoService pagamentos, EventosService eventos,
                           @Value("${taskgo.pagamentos.taxa-cancelamento:0.20}") BigDecimal taxaCancelamento,
                           @Value("${taskgo.pagamentos.taxa-cancelamento-minima:10}") BigDecimal cancelamentoMinimo,
                           @Value("${taskgo.pagamentos.taxa-cancelamento-maxima:50}") BigDecimal cancelamentoMaximo) {
        this.pendencias = pendencias; this.pedidos = pedidos; this.usuarios = usuarios; this.carteiras = carteiras; this.transacoes = transacoes;
        this.pagamentos = pagamentos; this.eventos = eventos;
        this.taxaCancelamento = taxaCancelamento; this.cancelamentoMinimo = cancelamentoMinimo; this.cancelamentoMaximo = cancelamentoMaximo;
    }
    @Autowired(required = false) void setNotificacoes(NotificacaoService n) { this.notificacoes = n; }
    @Autowired(required = false) void setModeracao(ModeracaoService m) { this.moderacao = m; }

    public static boolean emDinheiro(Pedido p) { return p != null && DINHEIRO.equals(p.getExtra().get(FORMA)); }
    public static boolean pixDireto(Pedido p) { return p != null && PIX_DIRETO.equals(p.getExtra().get(FORMA)); }
    /** Pago direto a quem atende (dinheiro ou Pix na chave dele): o valor não passa pela TaskGO. */
    public static boolean maquininha(Pedido p) { return p != null && MAQUININHA.equals(p.getExtra().get(FORMA)); }
    /** Pago direto a quem atende (dinheiro, Pix na chave dele ou cartão na maquininha dele): o valor não passa pela TaskGO. */
    public static boolean direto(Pedido p) { return emDinheiro(p) || pixDireto(p) || maquininha(p); }
    private ResgateService resgates;
    @Autowired(required = false) void setResgates(ResgateService r) { this.resgates = r; }

    /** Chave Pix de quem atende, para o cliente pagar direto (depois de confirmar o profissional). */
    @Transactional(readOnly = true)
    public Map<String, Object> chaveParaPagar(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!String.valueOf(quem.id()).equals(p.getClientId())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        /* O cliente só paga depois do serviço: a chave aparece com o serviço concluído ou com um valor em aberto. */
        boolean emAberto = pendencias.findByPedidoIdAndTipo(p.getId(), Pendencia.SERVICO_NAO_PAGO).filter(Pendencia::aberta).isPresent()
                || pendencias.findByPedidoIdAndTipo(p.getId(), Pendencia.TAXA_CANCELAMENTO).filter(Pendencia::aberta).isPresent();
        /* Obra e recorrente: há etapa aprovada, visita feita ou vez feita esperando o pagamento. */
        if (obras != null && ObraService.eh(p)) emAberto = emAberto || obras.temAPagar(p.getId()).signum() > 0;
        if (recorrentes != null && RecorrenteService.eh(p)) emAberto = emAberto || recorrentes.temAPagar(p.getId()).signum() > 0;
        boolean comum = !ObraService.eh(p) && !RecorrenteService.eh(p);
        if (!(emAberto || comum && p.getProEmail() != null && "concluido".equals(p.getStatus()) && p.getPaid() == null))
            throw erro(HttpStatus.CONFLICT, "A chave Pix aparece quando o serviço for concluído. Não pague antes.");
        Long proVisita = obras != null && ObraService.eh(p) && p.getProEmail() == null ? obras.proDaVisitaAPagar(p.getId()) : null;
        Usuario pro = (proVisita != null ? usuarios.findById(proVisita) : usuarios.findByEmailIgnoreCase(String.valueOf(p.getProEmail()))).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        ResgateService.ChavePix k = resgates == null ? null : resgates.chaveDe(pro.getId());
        if (k == null) throw erro(HttpStatus.CONFLICT, pro.getNome() + " ainda não cadastrou a chave Pix. Pague em dinheiro ou peça a chave pelo chat.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", pro.getNome()); m.put("tipo", k.tipo()); m.put("chave", k.chave()); m.put("valor", preco(p));
        if (obras != null && ObraService.eh(p)) m.put("valor", obras.temAPagar(p.getId()));
        if (recorrentes != null && RecorrenteService.eh(p)) m.put("valor", recorrentes.temAPagar(p.getId()));
        pendencias.findByPedidoIdAndTipo(p.getId(), Pendencia.SERVICO_NAO_PAGO).filter(Pendencia::aberta).ifPresent(x -> m.put("valor", x.restante()));
        pendencias.findByPedidoIdAndTipo(p.getId(), Pendencia.TAXA_CANCELAMENTO).filter(Pendencia::aberta).ifPresent(x -> m.put("valor", x.restante()));
        return m;
    }
    /** Taxa da TaskGO sobre um valor recebido direto (etapa de obra, visita técnica, semana do recorrente). */
    @Transactional
    public void registrarTaxa(Long trabalhadorId, String pedidoId, BigDecimal taxa) {
        if (trabalhadorId == null || taxa == null || taxa.signum() <= 0) return;
        Pendencia nova = pendencias.save(new Pendencia(UUID.randomUUID().toString(), trabalhadorId, null, pedidoId, Pendencia.TAXA_DINHEIRO, taxa.setScale(2, RoundingMode.HALF_UP), System.currentTimeMillis()));
        descontarTaxas(trabalhadorId);
        cobrarTaxaAutomatica(trabalhadorId, nova);
    }
    private ObraService obras;
    private RecorrenteService recorrentes;
    @Autowired(required = false) void setObras(@org.springframework.context.annotation.Lazy ObraService o) { this.obras = o; }
    @Autowired(required = false) void setRecorrentes(@org.springframework.context.annotation.Lazy RecorrenteService r) { this.recorrentes = r; }

    /** Quem atende precisa de chave Pix para aceitar pedido pago por Pix direto. */
    public void exigirChaveParaAceitar(Usuario quem, Pedido p) {
        if (!pixDireto(p) || resgates == null) return;
        if (resgates.chaveDe(quem.getId()) == null) throw erro(HttpStatus.CONFLICT, "Este cliente vai pagar por Pix direto para você. Cadastre sua chave Pix na Carteira para aceitar.");
    }

    /** Pagamento direto: o cliente pagou a pendência a quem atendeu, e quem atendeu confirma. */
    @Transactional
    public Map<String, Object> pendenciaRecebida(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = pedidoDoAtendente(pedidoId, quem);
        long agora = System.currentTimeMillis();
        Pendencia x = pendencias.findByPedidoIdAndTipo(p.getId(), Pendencia.SERVICO_NAO_PAGO).filter(Pendencia::aberta)
                .or(() -> pendencias.findByPedidoIdAndTipo(p.getId(), Pendencia.TAXA_CANCELAMENTO).filter(Pendencia::aberta))
                .orElseThrow(() -> erro(HttpStatus.CONFLICT, "Não há valor em aberto do cliente neste pedido."));
        if (!quem.id().equals(x.getCredorId())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        BigDecimal parte = x.restante();
        x.abater(parte, agora); pendencias.save(x);
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), x.getUsuarioId(), "PENDENCIA_PAGA_DIRETO", parte, "CONCLUIDA", p.getId(), agora));
        /* Serviço feito e pago depois: a taxa da TaskGO vira taxa a pagar de quem atendeu. */
        if (Pendencia.SERVICO_NAO_PAGO.equals(x.getTipo())) {
            BigDecimal taxa = parte.multiply(pagamentos.taxaDe(p)).setScale(2, RoundingMode.HALF_UP);
            if (taxa.signum() > 0) { Pendencia nova = pendencias.save(new Pendencia(UUID.randomUUID().toString(), quem.id(), null, p.getId(), Pendencia.TAXA_DINHEIRO, taxa, agora)); descontarTaxas(quem.id()); cobrarTaxaAutomatica(quem.id(), nova); }
        }
        p.setPaid("pago_depois"); pedidos.save(p);
        avisar(x.getUsuarioId(), p.getId(), "Pendência quitada", "Quem atendeu confirmou que recebeu " + reais(parte) + "." + (devendo(x.getUsuarioId()).signum() > 0 ? " Ainda há valores em aberto." : " Você já pode fazer novos pedidos."), "pendencia-direta:" + x.getId());
        eventos.enviar(x.getUsuarioId(), "pedido", p.getId());
        auditar(x.getUsuarioId(), "Pagou direto a pendência de " + reais(parte), quem.id(), p.getId());
        return Map.of("recebido", parte);
    }

    /* ---------------- bloqueio do cliente ---------------- */

    /** Quanto o cliente deve de serviços não pagos e taxas de cancelamento. */
    @Transactional(readOnly = true)
    public BigDecimal devendo(Long usuarioId) {
        return pendencias.findByUsuarioIdAndStatusOrderByCriadaEmAsc(usuarioId, Pendencia.ABERTA).stream()
                .filter(x -> DO_CLIENTE.contains(x.getTipo())).map(Pendencia::restante).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public void exigirSemPendencia(Long usuarioId) {
        BigDecimal d = devendo(usuarioId);
        if (d.signum() > 0) throw erro(HttpStatus.CONFLICT, "Você tem " + reais(d) + " em aberto de um serviço não pago. " + (pagamentos.direto() ? "Pague direto a quem atendeu (a chave Pix está no pedido) para fazer novos pedidos." : "Pague na Carteira, com cartão ou com o saldo da carteira, para fazer novos pedidos."));
    }

    public BigDecimal taxaCancelamento(BigDecimal preco) {
        BigDecimal t = preco.multiply(taxaCancelamento).setScale(2, RoundingMode.HALF_UP).max(cancelamentoMinimo).min(cancelamentoMaximo);
        return t.min(preco).setScale(2, RoundingMode.HALF_UP);
    }

    /* ---------------- quem atendeu recebe em dinheiro ---------------- */

    @Transactional
    public Map<String, Object> receber(String pedidoId, TaskgoPrincipal quem, BigDecimal recebido, boolean trocoOnline) {
        Pedido p = pedidoDoAtendente(pedidoId, quem);
        if (!direto(p)) throw erro(HttpStatus.CONFLICT, "Este pedido não é pago direto a quem atende.");
        if (!"concluido".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Conclua o serviço antes de registrar o pagamento.");
        boolean pix = pixDireto(p), cartao = maquininha(p);
        if (pix || cartao) { recebido = null; trocoOnline = false; }
        String forma = pix ? PIX_DIRETO : cartao ? MAQUININHA : DINHEIRO;
        if ("estornando".equals(p.getPaid())) throw erro(HttpStatus.CONFLICT, "A devolução do pagamento online ainda está em andamento. Tente de novo em alguns minutos.");
        if (p.getPaid() != null && !"estornado".equals(p.getPaid())) throw erro(HttpStatus.CONFLICT, "O pagamento deste pedido já foi registrado.");
        BigDecimal preco = preco(p);
        BigDecimal entregue = (recebido == null ? preco : recebido).setScale(2, RoundingMode.HALF_UP);
        if (entregue.compareTo(preco) < 0) throw erro(HttpStatus.BAD_REQUEST, "O cliente precisa entregar pelo menos " + reais(preco) + ". Se ele não pagou, use Cliente não pagou.");
        if (entregue.compareTo(preco.multiply(BigDecimal.TEN).max(BigDecimal.valueOf(1000))) > 0) throw erro(HttpStatus.BAD_REQUEST, "Confira o valor recebido.");
        BigDecimal troco = entregue.subtract(preco);
        Long trabalhador = quem.id(), cliente = clienteId(p);
        long agora = System.currentTimeMillis();
        boolean online = trocoOnline && troco.signum() > 0;
        if (online) {
            if (cliente == null) throw erro(HttpStatus.CONFLICT, "Cliente não encontrado para receber o troco.");
            Carteira minha = carteiras.findByUsuarioIdForUpdate(trabalhador).orElse(null);
            BigDecimal saldo = minha == null ? BigDecimal.ZERO : minha.getSaldoDisponivel();
            if (minha == null || saldo.compareTo(troco) < 0)
                throw erro(HttpStatus.CONFLICT, "Seu saldo (" + reais(saldo) + ") não cobre o troco online de " + reais(troco) + ". Dê o troco em espécie.");
            minha.debitar(troco); carteiras.save(minha);
            Carteira doCliente = carteiras.findByUsuarioIdForUpdate(cliente).orElseGet(() -> carteiras.save(new Carteira(cliente)));
            doCliente.creditar(troco); carteiras.save(doCliente);
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), trabalhador, "TROCO_ENVIADO", troco, "CONCLUIDA", p.getId(), agora));
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), cliente, "TROCO_RECEBIDO", troco, "CONCLUIDA", p.getId(), agora));
            avisar(cliente, p.getId(), "Troco na sua carteira", reais(troco) + " de troco do serviço estão na sua carteira TaskGO. Use para pagar os próximos pedidos.", "troco:" + p.getId());
        }
        BigDecimal taxa = preco.multiply(pagamentos.taxaDe(p)).setScale(2, RoundingMode.HALF_UP);
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), trabalhador, pix ? "RECEBIDO_PIX_DIRETO" : cartao ? "RECEBIDO_MAQUININHA" : "RECEBIDO_DINHEIRO", preco, "CONCLUIDA", p.getId(), agora));
        if (taxa.signum() > 0) {
            Pendencia nova = pendencias.save(new Pendencia(UUID.randomUUID().toString(), trabalhador, null, p.getId(), Pendencia.TAXA_DINHEIRO, taxa, agora));
            descontarTaxas(trabalhador);
            cobrarTaxaAutomatica(trabalhador, nova);
        }
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("recebido", entregue); registro.put("troco", troco); registro.put("trocoOnline", online); registro.put("taxa", taxa); registro.put("em", agora); registro.put("forma", forma);
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put("dinheiro", registro); e.put("liberacao", "dinheiro"); p.setExtra(e);
        p.setPaid(forma);
        pedidos.save(p);
        auditar(trabalhador, "Recebeu " + reais(preco) + (pix ? " por Pix direto" : cartao ? " no cartão (maquininha)" : " em dinheiro") + (online ? " (troco online de " + reais(troco) + ")" : troco.signum() > 0 ? " (troco de " + reais(troco) + " em espécie)" : "") + "; taxa de " + reais(taxa), trabalhador, p.getId());
        if (cliente != null) {
            avisar(cliente, p.getId(), "Pagamento registrado", "Quem atendeu confirmou que recebeu " + reais(entregue) + (pix ? " por Pix." : cartao ? " no cartão." : " em dinheiro."), "dinheiro:" + p.getId());
            eventos.enviar(cliente, "pagamento", p.getId());
        }
        return Map.of("recebido", entregue, "troco", troco, "trocoOnline", online, "taxa", taxa);
    }

    /* ---------------- cliente não pagou ---------------- */

    @Transactional
    public Pedido naoPago(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = pedidoDoAtendente(pedidoId, quem);
        if (!direto(p)) throw erro(HttpStatus.CONFLICT, "Este pedido não é pago direto a quem atende.");
        if (p.getPaid() != null && !"estornado".equals(p.getPaid())) throw erro(HttpStatus.CONFLICT, "O pagamento deste pedido já foi registrado.");
        if (!java.util.Set.of("chegou", "em_andamento", "concluido").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Só dá para informar depois de chegar ao local.");
        Long cliente = clienteId(p);
        if (cliente == null) throw erro(HttpStatus.CONFLICT, "Cliente não encontrado.");
        boolean feito = !"chegou".equals(p.getStatus());
        BigDecimal preco = preco(p), valor = feito ? preco : taxaCancelamento(preco);
        long agora = System.currentTimeMillis();
        String tipo = feito ? Pendencia.SERVICO_NAO_PAGO : Pendencia.TAXA_CANCELAMENTO;
        if (pendencias.findByPedidoIdAndTipo(p.getId(), tipo).isEmpty())
            pendencias.save(new Pendencia(UUID.randomUUID().toString(), cliente, quem.id(), p.getId(), tipo, valor, agora));
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put("naoPago", Map.of("valor", valor, "tipo", tipo, "em", agora)); p.setExtra(e);
        p.setStatus("cancelado"); p.setCancelReason("Cliente não pagou"); p.setPaid("nao_pago");
        Pedido salvo = pedidos.save(p);
        auditar(cliente, (feito ? "Não pagou o serviço" : "Recusou pagar ao receber quem atendeu") + ": pendência de " + reais(valor), quem.id(), p.getId());
        avisar(cliente, p.getId(), "Pendência de " + reais(valor),
                (feito ? "O serviço foi marcado como não pago." : "O atendimento foi cancelado por falta de pagamento; ficou a taxa de cancelamento.") +
                " Enquanto não pagar, você não consegue fazer novos pedidos. " + (pagamentos.direto() ? "Pague direto a quem atendeu: a chave Pix está no pedido." : "Pague na Carteira."), "nao-pago:" + p.getId());
        eventos.enviar(cliente, "pedido", p.getId());
        eventos.enviar(quem.id(), "pedido", p.getId());
        return salvo;
    }

    /* ---------------- trocar a forma de pagamento (uma vez) ---------------- */

    @Transactional
    public Object trocarForma(String pedidoId, Long clienteId, String metodo, String cartaoId) {
        if (pagamentos.direto()) return trocarFormaDireta(pedidoId, clienteId, metodo);
        if (!pagamentos.ativo()) throw erro(HttpStatus.CONFLICT, "O pagamento online está desligado.");
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!String.valueOf(clienteId).equals(p.getClientId())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        if ("cancelado".equals(p.getStatus()) || "aguardando_pagamento".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Não dá para trocar a forma de pagamento deste pedido.");
        if (Boolean.TRUE.equals(p.getExtra().get("trocouForma"))) throw erro(HttpStatus.CONFLICT, "Você já trocou a forma de pagamento deste pedido uma vez.");
        Long atendente = p.getProEmail() == null ? null : usuarios.findByEmailIgnoreCase(p.getProEmail()).map(Usuario::getId).orElse(null);
        BigDecimal preco = preco(p);
        if (emDinheiro(p)) {
            if (p.getPaid() != null && !"estornado".equals(p.getPaid())) throw erro(HttpStatus.CONFLICT, "O pagamento deste pedido já foi registrado.");
            if (metodo == null || DINHEIRO.equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Escolha cartão, Pix ou a carteira TaskGO.");
            Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put(FORMA, ONLINE); e.put("trocouForma", true); e.put("pagamentoPendente", true); p.setExtra(e);
            p.setPaid(null);
            pedidos.save(p);
            Cobranca c = pagamentos.cobrar(p, clienteId, metodo, cartaoId, 1, preco, "complementar");
            if (Cobranca.RECUSADA.equals(c.getStatus())) {
                /* Cartão recusado: volta para dinheiro e a troca não conta. */
                Pedido atual = pedidos.findById(pedidoId).orElseThrow();
                Map<String, Object> v = new LinkedHashMap<>(atual.getExtra()); v.put(FORMA, DINHEIRO); v.remove("trocouForma"); v.remove("pagamentoPendente"); atual.setExtra(v);
                pedidos.save(atual);
                throw erro(HttpStatus.PAYMENT_REQUIRED, c.getMotivo() == null ? "O cartão não foi aprovado. O pedido continua em dinheiro." : c.getMotivo() + " O pedido continua em dinheiro.");
            }
            if (atendente != null) {
                avisar(atendente, p.getId(), "O cliente vai pagar online", "Não receba em dinheiro: o valor de " + reais(preco) + " vem pela TaskGO.", "troca-online:" + p.getId());
                eventos.enviar(atendente, "pedido", p.getId());
            }
            auditar(clienteId, "Trocou o pagamento de dinheiro para online", clienteId, p.getId());
            return PagamentoService.CobrancaDto.de(c);
        }
        if (!DINHEIRO.equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Para trocar, escolha dinheiro.");
        if (preco.compareTo(PagamentoService.MIL) >= 0) throw erro(HttpStatus.BAD_REQUEST, "A partir de R$ 1.000 não dá para pagar em dinheiro.");
        if ("concluido".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "O serviço já foi concluído.");
        if (!"retido".equals(p.getPaid())) throw erro(HttpStatus.CONFLICT, "Este pedido não tem pagamento online para trocar.");
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put(FORMA, DINHEIRO); e.put("trocouForma", true); e.remove("pagamentoPendente"); p.setExtra(e);
        pedidos.save(p);
        pagamentos.estornar(p);
        Pedido atual = pedidos.findById(pedidoId).orElseThrow();
        if ("estornado".equals(atual.getPaid())) { atual.setPaid(null); pedidos.save(atual); }
        if (atendente != null) {
            avisar(atendente, p.getId(), "O cliente vai pagar em dinheiro", "Receba " + reais(preco) + " em dinheiro no fim do serviço.", "troca-dinheiro:" + p.getId());
            eventos.enviar(atendente, "pedido", p.getId());
        }
        auditar(clienteId, "Trocou o pagamento online para dinheiro (valor devolvido)", clienteId, p.getId());
        return Map.of("forma", DINHEIRO);
    }

    /* Modelo direto: troca entre Pix para quem atende e dinheiro, uma vez, antes de registrar o pagamento. */
    private Object trocarFormaDireta(String pedidoId, Long clienteId, String metodo) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!String.valueOf(clienteId).equals(p.getClientId())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        if (!direto(p) || "cancelado".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Não dá para trocar a forma de pagamento deste pedido.");
        if (p.getPaid() != null) throw erro(HttpStatus.CONFLICT, "O pagamento deste pedido já foi registrado.");
        if (Boolean.TRUE.equals(p.getExtra().get("trocouForma"))) throw erro(HttpStatus.CONFLICT, "Você já trocou a forma de pagamento deste pedido uma vez.");
        if (!FORMAS_DIRETAS.contains(String.valueOf(metodo))) throw erro(HttpStatus.BAD_REQUEST, "Escolha Pix, cartão na maquininha ou dinheiro.");
        if (metodo.equals(p.getExtra().get(FORMA))) throw erro(HttpStatus.BAD_REQUEST, "O pedido já está nessa forma de pagamento.");
        if (DINHEIRO.equals(metodo) && preco(p).compareTo(PagamentoService.MIL) >= 0) throw erro(HttpStatus.BAD_REQUEST, "A partir de R$ 1.000 não dá para pagar em dinheiro.");
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put(FORMA, metodo); e.put("trocouForma", true); p.setExtra(e);
        pedidos.save(p);
        Long atendente = p.getProEmail() == null ? null : usuarios.findByEmailIgnoreCase(p.getProEmail()).map(Usuario::getId).orElse(null);
        if (atendente != null) {
            String como = DINHEIRO.equals(metodo) ? " em dinheiro" : MAQUININHA.equals(metodo) ? " no cartão, na sua maquininha" : " por Pix na sua chave";
            avisar(atendente, p.getId(), "O cliente trocou a forma de pagamento", "Receba " + reais(preco(p)) + como + " no fim do serviço.", "troca-direta:" + p.getId());
            eventos.enviar(atendente, "pedido", p.getId());
        }
        auditar(clienteId, "Trocou o pagamento para " + (DINHEIRO.equals(metodo) ? "dinheiro" : MAQUININHA.equals(metodo) ? "cartão na maquininha" : "Pix direto"), clienteId, p.getId());
        return Map.of("forma", metodo);
    }

    /* ---------------- cobrança automática da taxa ---------------- */

    public static final String CARTAO_TAXA = "cartaoTaxa";
    /** Cartão que quem atende autorizou para a TaskGO cobrar as taxas sozinha. */
    public String cartaoAutorizado(Long usuarioId) {
        return usuarios.findById(usuarioId).map(u -> u.getExtra().get(CARTAO_TAXA)).map(String::valueOf).orElse(null);
    }
    /** Liga (com o cartão escolhido) ou desliga a cobrança automática; ao ligar, já cobra as taxas em aberto. */
    @Transactional
    public Map<String, Object> autorizarCobranca(Long usuarioId, String cartaoId, boolean autorizado) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Conta não encontrada."));
        if (cartaoId == null || cartaoId.isBlank()) {
            u.getExtra().remove(CARTAO_TAXA); usuarios.save(u);
            auditar(usuarioId, "Desligou a cobrança automática da taxa", usuarioId, null);
            return Map.of("cartao", "");
        }
        if (!autorizado) throw erro(HttpStatus.BAD_REQUEST, "Marque a autorização para a TaskGO cobrar as taxas neste cartão.");
        if (pagamentos.listarCartoes(usuarioId).stream().noneMatch(c -> c.id().equals(cartaoId))) throw erro(HttpStatus.BAD_REQUEST, "Escolha um dos seus cartões.");
        u.getExtra().put(CARTAO_TAXA, cartaoId); usuarios.save(u);
        auditar(usuarioId, "Autorizou a cobrança automática da taxa no cartão", usuarioId, null);
        for (Pendencia x : pendencias.findByUsuarioIdAndStatusOrderByCriadaEmAsc(usuarioId, Pendencia.ABERTA))
            if (Pendencia.TAXA_DINHEIRO.equals(x.getTipo())) cobrarTaxaAutomatica(usuarioId, x);
        return Map.of("cartao", cartaoId);
    }
    /* Cobra a taxa no cartão autorizado. Recusou: a taxa fica em aberto e quem atende é avisado para pagar por Pix ou trocar o cartão. */
    void cobrarTaxaAutomatica(Long trabalhadorId, Pendencia x) {
        if (x == null || !x.aberta() || !pagamentos.ativo()) return;
        String cartao = cartaoAutorizado(trabalhadorId);
        if (cartao == null || cartao.isBlank()) return;
        Pedido p = pedidos.findById(x.getPedidoId()).orElse(null);
        if (p == null) return;
        Cobranca c;
        try { c = pagamentos.cobrar(p, trabalhadorId, "cartao", cartao, 1, x.restante(), "pendencia"); }
        catch (RuntimeException falhou) { avisarRecusa(trabalhadorId, x, "O cartão autorizado não pôde ser cobrado agora."); return; }
        if (Cobranca.RECUSADA.equals(c.getStatus())) avisarRecusa(trabalhadorId, x, c.getMotivo() == null ? "O cartão autorizado foi recusado." : c.getMotivo());
    }
    private void avisarRecusa(Long trabalhadorId, Pendencia x, String motivo) {
        avisar(trabalhadorId, x.getPedidoId(), "Taxa não cobrada no cartão", motivo + " Pague a taxa de " + reais(x.restante()) + " por Pix na Carteira ou troque o cartão.", "taxa-recusada:" + x.getId());
    }

    /* ---------------- pendências ---------------- */

    @Transactional(readOnly = true)
    public Map<String, Object> minhas(Long usuarioId) {
        List<PendenciaDto> lista = pendencias.findByUsuarioIdOrderByCriadaEmDesc(usuarioId).stream().limit(50).map(this::dto).toList();
        BigDecimal taxas = lista.stream().filter(x -> Pendencia.ABERTA.equals(x.status()) && Pendencia.TAXA_DINHEIRO.equals(x.tipo())).map(PendenciaDto::restante).reduce(BigDecimal.ZERO, BigDecimal::add);
        String cartao = cartaoAutorizado(usuarioId);
        return Map.of("pendencias", lista, "devendo", devendo(usuarioId), "taxasAbertas", taxas, "cartaoTaxa", cartao == null ? "" : cartao);
    }

    @Transactional
    public Object pagarPendencia(String id, Long usuarioId, String metodo, String cartaoId) {
        Pendencia x = pendencias.findById(id).filter(y -> y.getUsuarioId().equals(usuarioId)).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pendência não encontrada."));
        if (!x.aberta()) throw erro(HttpStatus.CONFLICT, "Esta pendência já foi paga.");
        boolean doCliente = DO_CLIENTE.contains(x.getTipo());
        if (doCliente && pagamentos.direto()) throw erro(HttpStatus.CONFLICT, "Pague direto a quem atendeu: a chave Pix está no pedido. Quem atendeu confirma o recebimento.");
        if (doCliente && !java.util.Set.of("cartao", "cartao_novo", "carteira").contains(String.valueOf(metodo)))
            throw erro(HttpStatus.BAD_REQUEST, "Pague com cartão de crédito ou com o saldo da carteira TaskGO.");
        Pedido p = pedidos.findById(x.getPedidoId()).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        Cobranca c = pagamentos.cobrar(p, usuarioId, metodo, cartaoId, 1, x.restante(), "pendencia");
        pendencias.findById(id).ifPresent(atual -> { if (atual.aberta()) { atual.tentativa(c.getId()); pendencias.save(atual); } });
        if (Cobranca.RECUSADA.equals(c.getStatus())) throw erro(HttpStatus.PAYMENT_REQUIRED, c.getMotivo() == null ? "O cartão não foi aprovado." : c.getMotivo());
        return PagamentoService.CobrancaDto.de(c);
    }

    /** Chamado pelo PagamentoService quando uma cobrança de pendência é confirmada. */
    @Transactional
    public void pendenciaPaga(Cobranca c) {
        long agora = System.currentTimeMillis();
        Pendencia x = pendencias.findByUsuarioIdAndStatusOrderByCriadaEmAsc(c.getClienteId(), Pendencia.ABERTA).stream()
                .filter(y -> y.getPedidoId().equals(c.getPedidoId())).findFirst().orElse(null);
        if (x == null) {
            /* Já estava paga (pagou duas vezes): o valor vai para a carteira de quem pagou. */
            Carteira cart = carteiras.findByUsuarioIdForUpdate(c.getClienteId()).orElseGet(() -> carteiras.save(new Carteira(c.getClienteId())));
            cart.creditar(c.getValor()); carteiras.save(cart);
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "ESTORNO", c.getValor(), "CONCLUIDA", c.getPedidoId(), agora));
            return;
        }
        BigDecimal parte = c.getValor().min(x.restante());
        x.abater(parte, agora);
        pendencias.save(x);
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "PENDENCIA_PAGA", parte, "CONCLUIDA", x.getPedidoId(), agora));
        Pedido p = pedidos.findById(x.getPedidoId()).orElse(null);
        if (x.getCredorId() != null) {
            BigDecimal taxa = Pendencia.SERVICO_NAO_PAGO.equals(x.getTipo()) && p != null ? parte.multiply(pagamentos.taxaDe(p)).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal repasse = parte.subtract(taxa);
            if (repasse.signum() > 0) {
                Carteira cart = carteiras.findByUsuarioIdForUpdate(x.getCredorId()).orElseGet(() -> carteiras.save(new Carteira(x.getCredorId())));
                cart.creditar(repasse); carteiras.save(cart);
                transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), x.getCredorId(), "REPASSE", repasse, "CONCLUIDA", x.getPedidoId(), agora));
                avisar(x.getCredorId(), x.getPedidoId(), "Pendência paga", "O cliente pagou o que devia. " + reais(repasse) + " caíram na sua carteira" + (taxa.signum() > 0 ? " (taxa de " + reais(taxa) + ")" : "") + ".", "pendencia-recebida:" + x.getId());
                descontarTaxas(x.getCredorId());
            }
        }
        if (!x.aberta() && p != null && "nao_pago".equals(p.getPaid())) { p.setPaid("pago_depois"); pedidos.save(p); }
        avisar(c.getClienteId(), x.getPedidoId(), DO_CLIENTE.contains(x.getTipo()) ? "Pendência paga" : "Taxa paga",
                DO_CLIENTE.contains(x.getTipo()) ? (devendo(c.getClienteId()).signum() > 0 ? "Recebemos " + reais(parte) + ". Ainda há valores em aberto." : "Recebemos " + reais(parte) + ". Você já pode fazer novos pedidos.") : "Recebemos " + reais(parte) + " da taxa do serviço em dinheiro.",
                "pendencia-paga:" + x.getId() + ":" + c.getId());
        auditar(c.getClienteId(), "Pagou pendência de " + reais(parte), c.getClienteId(), x.getPedidoId());
    }

    /* ---------------- taxas de quem atendeu ---------------- */

    /** Desconta do saldo disponível as taxas de serviços recebidos em dinheiro. Devolve quanto descontou. */
    @Transactional
    public BigDecimal descontarTaxas(Long trabalhadorId) {
        List<Pendencia> abertas = pendencias.findByUsuarioIdAndStatusOrderByCriadaEmAsc(trabalhadorId, Pendencia.ABERTA).stream()
                .filter(x -> Pendencia.TAXA_DINHEIRO.equals(x.getTipo())).toList();
        if (abertas.isEmpty()) return BigDecimal.ZERO;
        Carteira cart = carteiras.findByUsuarioIdForUpdate(trabalhadorId).orElse(null);
        if (cart == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        long agora = System.currentTimeMillis();
        for (Pendencia x : abertas) {
            BigDecimal parte = cart.getSaldoDisponivel().min(x.restante());
            if (parte.signum() <= 0) break;
            cart.debitar(parte);
            x.abater(parte, agora);
            pendencias.save(x);
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), trabalhadorId, "TAXA_DINHEIRO", parte, "CONCLUIDA", x.getPedidoId(), agora));
            total = total.add(parte);
        }
        carteiras.save(cart);
        return total;
    }

    /** Balanço semanal (segunda, 6h): desconta o que der do saldo e avisa o que falta pagar. */
    @Scheduled(cron = "0 0 6 * * MON", zone = "America/Recife")
    @Transactional
    public void balancoSemanal() {
        Map<Long, List<Pendencia>> porConta = pendencias.findByStatusAndTipo(Pendencia.ABERTA, Pendencia.TAXA_DINHEIRO).stream()
                .collect(Collectors.groupingBy(Pendencia::getUsuarioId));
        LocalDate hoje = LocalDate.now(PagamentoService.FUSO);
        String semana = hoje.getYear() + "-" + hoje.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        porConta.keySet().forEach(conta -> {
            BigDecimal descontado = descontarTaxas(conta);
            BigDecimal resta = pendencias.findByUsuarioIdAndStatusOrderByCriadaEmAsc(conta, Pendencia.ABERTA).stream()
                    .filter(x -> Pendencia.TAXA_DINHEIRO.equals(x.getTipo())).map(Pendencia::restante).reduce(BigDecimal.ZERO, BigDecimal::add);
            avisar(conta, null, "Balanço semanal",
                    resta.signum() > 0 ? (descontado.signum() > 0 ? "Descontamos " + reais(descontado) + " do seu saldo. " : "") + "Ficaram " + reais(resta) + " de taxas de serviços em dinheiro. Pague na Carteira."
                            : "As taxas dos serviços em dinheiro (" + reais(descontado) + ") foram descontadas do seu saldo.",
                    "balanco:" + conta + ":" + semana);
        });
    }

    /* ---------------- apoio ---------------- */

    private Pedido pedidoDoAtendente(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (p.getProEmail() == null || !quem.email().equalsIgnoreCase(p.getProEmail())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        return p;
    }
    private static BigDecimal preco(Pedido p) { return BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice()).setScale(2, RoundingMode.HALF_UP); }
    private static Long clienteId(Pedido p) { try { return Long.valueOf(p.getClientId()); } catch (RuntimeException e) { return null; } }
    private PendenciaDto dto(Pendencia x) {
        String servico = pedidos.findById(x.getPedidoId()).map(p -> PedidoContratadoController.nomeServico(p.getService())).orElse("Serviço");
        return new PendenciaDto(x.getId(), x.getTipo(), x.getPedidoId(), servico, x.getValor(), x.getPago(), x.restante(), x.getStatus(), x.getCriadaEm());
    }
    private void avisar(Long usuarioId, String pedidoId, String titulo, String texto, String chave) {
        if (notificacoes == null || usuarioId == null) return;
        try { notificacoes.criar(usuarioId, pedidoId, pedidoId == null ? "conta" : "pedido", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
    private void auditar(Long conta, String texto, Long autor, String pedidoId) { if (moderacao != null && conta != null) moderacao.registrar(conta, "pagamento", texto, autor, pedidoId); }
    static String reais(BigDecimal v) { return PagamentoService.reais(v); }
    private static ResponseStatusException erro(HttpStatus s, String m) { return new ResponseStatusException(s, m); }

    public record PendenciaDto(String id, String tipo, String pedidoId, String servico, BigDecimal valor, BigDecimal pago, BigDecimal restante, String status, long criadaEm) {}
}
