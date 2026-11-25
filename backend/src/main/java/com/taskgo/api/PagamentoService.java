package com.taskgo.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* Pagamento dos pedidos. O cliente paga antes de o pedido procurar profissional (cartão salvo: na hora; Pix: antes de publicar).
   A TaskGO segura o valor; quando o serviço é concluído, o profissional recebe o valor menos a taxa, na carteira.
   Cancelou: o que foi pago volta. Proposta maior: cobra a diferença; menor: devolve a sobra ao concluir. */
@Service
public class PagamentoService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PagamentoService.class);
    static final ZoneId FUSO = ZoneId.of("America/Recife");
    static final int PIX_EXPIRA_SEGUNDOS = 3600;

    private final String modo;
    private final BigDecimal taxa;
    private final CobrancaRepository cobrancas;
    private final CartaoRepository cartoes;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final CarteiraRepository carteiras;
    private final TransacaoFinanceiraRepository transacoes;
    private final ProvedorPix pix;
    private final EventosService eventos;
    private final PublicacaoPedidoService publicacao;
    private NotificacaoService notificacoes;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public PagamentoService(@Value("${taskgo.pagamentos.modo:desligado}") String modo,
                            @Value("${taskgo.pagamentos.taxa:0.15}") BigDecimal taxa,
                            CobrancaRepository cobrancas, CartaoRepository cartoes, PedidoContratadoRepository pedidos,
                            UsuarioRepository usuarios, CarteiraRepository carteiras, TransacaoFinanceiraRepository transacoes,
                            ProvedorPix pix, EventosService eventos, PublicacaoPedidoService publicacao) {
        this.modo = modo == null ? "desligado" : modo.trim().toLowerCase(Locale.ROOT);
        this.taxa = taxa; this.cobrancas = cobrancas; this.cartoes = cartoes; this.pedidos = pedidos; this.usuarios = usuarios;
        this.carteiras = carteiras; this.transacoes = transacoes; this.pix = pix; this.eventos = eventos; this.publicacao = publicacao;
    }
    @Autowired(required = false) void setNotificacoes(NotificacaoService n) { this.notificacoes = n; }
    private DinheiroService dinheiro;
    private ObraService obras;
    @Autowired(required = false) void setObras(@org.springframework.context.annotation.Lazy ObraService o) { this.obras = o; }
    private RecorrenteService recorrentes;
    @Autowired(required = false) void setRecorrentes(@org.springframework.context.annotation.Lazy RecorrenteService r) { this.recorrentes = r; }
    /** Devolve parte de uma cobrança paga (obra: etapa ou visita). Com transação de quem chama, o Asaas é chamado depois do commit:
        aqui sai EstornoAdiado com a referência da reserva, e quem chama registra "em andamento". Sem transação, devolve na hora. */
    public void devolverCobranca(Cobranca c, BigDecimal parte) {
        BigDecimal quanto = parte.min(c.liquido());
        if (quanto.signum() > 0) estornarCobranca(c, quanto, System.currentTimeMillis());
    }
    /** Cancela uma cobrança ainda em aberto (Pix ou página do Asaas). */
    @Transactional
    public void cancelarAberta(Cobranca c) { cancelarAberta(c, "Obra cancelada."); }
    @Transactional
    public void cancelarAberta(Cobranca c, String motivo) { if (Cobranca.PENDENTE.equals(c.getStatus())) fecharAberta(c, motivo); }
    /* Fecha uma cobrança aberta. Se o provedor não confirmar o cancelamento (pode ter sido paga agora), ela fica pendente
       e a confirmação do pagamento segue o caminho normal (pedido cancelado devolve). */
    private void fecharAberta(Cobranca c, String motivo) {
        if (cancelarNoProvedor(c)) { c.cancelar(motivo); cobrancas.save(c); }
        else conferirPix(c);
    }
    public java.util.Optional<Cobranca> cobranca(String id) { return id == null ? java.util.Optional.empty() : cobrancas.findById(id); }
    @Autowired(required = false) void setDinheiro(@org.springframework.context.annotation.Lazy DinheiroService d) { this.dinheiro = d; }
    /* Para reler uma cobrança que outra transação acabou de confirmar. */
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    /* Plano Pro: taxa menor de quem assina e a mensalidade, que passa pelo mesmo caminho de cobrança. */
    private PlanoService planos;
    @Autowired(required = false) void setPlanos(@org.springframework.context.annotation.Lazy PlanoService p) { this.planos = p; }
    private AsaasCliente asaas;
    @Autowired(required = false) void setAsaas(AsaasCliente a) { this.asaas = a; }
    /* Modo "asaas": pagamento real (sandbox ou produção, conforme a URL configurada). */
    public boolean asaasAtivo() { return "asaas".equals(modo) && asaas != null; }
    /* Modo de teste: simulador interno ou sandbox do Asaas (mostra a faixa e o botão "Simular pagamento"). */
    public boolean teste() { return simulado() || asaasAtivo() && asaas.sandbox(); }
    private ModeracaoService moderacao;
    @Autowired(required = false) void setModeracao(ModeracaoService m) { this.moderacao = m; }
    /* Auditoria dos pagamentos (fica na conta de quem é afetado). */
    private void auditar(Long conta, String texto, Long autor, String pedidoId) { if (moderacao != null && conta != null) moderacao.registrar(conta, "pagamento", texto, autor, pedidoId); }

    public boolean ativo() { return !"desligado".equals(modo); }
    public boolean simulado() { return "simulado".equals(modo); }
    public String modo() { return modo; }
    public BigDecimal taxa() { return taxa; }
    /* Ajuda rápida tem taxa própria (10%); os demais serviços, a taxa configurada. */
    /* Ajuda rápida: 10%. Obra e pedido comum a partir de R$ 1.000: taxa da faixa (12% até 2.999). Abaixo de R$ 1.000: a taxa configurada (15%). */
    /* Plano Pro: 10% nos pedidos comuns e no recorrente (no lugar de 15% e 12%), enquanto o plano de quem atende valer. */
    public BigDecimal taxaDe(Pedido p) {
        if (AjudaRapida.eh(p)) return AjudaRapida.TAXA;
        BigDecimal preco = BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice());
        if (ObraService.eh(p)) return FaixaObra.taxa(preco);
        if (planos != null && planos.ehProEmail(p.getProEmail())) return PlanoService.TAXA_PRO;
        return preco.compareTo(MIL) >= 0 ? FaixaObra.taxa(preco) : taxa;
    }

    /* ---------------- cartões ---------------- */

    @Transactional(readOnly = true)
    public List<CartaoDto> listarCartoes(Long usuarioId) { return cartoes.findByUsuarioIdOrderByCriadoEmAsc(usuarioId).stream().map(CartaoDto::de).toList(); }

    @Transactional
    public CartaoDto salvarCartao(Long usuarioId, NovoCartao n) {
        exigirAtivo();
        /* Com o Asaas, o número do cartão nunca passa pelo nosso servidor: o cartão é salvo depois do primeiro pagamento na página dele. */
        if (asaasAtivo()) throw erro(HttpStatus.CONFLICT, "Pague uma vez com cartão na página segura e ele fica salvo para as próximas.");
        if (n == null) throw erro(HttpStatus.BAD_REQUEST, "Preencha os dados do cartão.");
        String digitos = CartaoSimulado.somenteDigitos(n.numero());
        if (!CartaoSimulado.luhn(digitos)) throw erro(HttpStatus.BAD_REQUEST, "Número de cartão inválido. Confira os dígitos.");
        String bandeira = CartaoSimulado.bandeira(digitos);
        if (bandeira == null) throw erro(HttpStatus.BAD_REQUEST, "Bandeira não aceita. Use Visa, Mastercard, Elo, Amex ou Hipercard.");
        int mes = n.mes() == null ? 0 : n.mes(), ano = n.ano() == null ? 0 : n.ano();
        if (ano < 100) ano += 2000;
        if (mes < 1 || mes > 12 || ano > YearMonth.now(FUSO).getYear() + 20) throw erro(HttpStatus.BAD_REQUEST, "Validade inválida. Use o mês e o ano impressos no cartão.");
        if (YearMonth.of(ano, mes).isBefore(YearMonth.now(FUSO))) throw erro(HttpStatus.BAD_REQUEST, "Este cartão está vencido.");
        String cvv = CartaoSimulado.somenteDigitos(n.cvv());
        if (cvv.length() != ("Amex".equals(bandeira) ? 4 : 3)) throw erro(HttpStatus.BAD_REQUEST, "Código de segurança inválido. São os " + ("Amex".equals(bandeira) ? "4" : "3") + " números no cartão.");
        String titular = n.titular() == null ? "" : n.titular().trim().replaceAll("\\s+", " ");
        if (titular.length() < 3 || titular.length() > 80) throw erro(HttpStatus.BAD_REQUEST, "Informe o nome como está impresso no cartão.");
        if (cartoes.countByUsuarioId(usuarioId) >= 5) throw erro(HttpStatus.CONFLICT, "Você já tem 5 cartões. Remova um para adicionar outro.");
        /* O número completo e o CVV param aqui: só o token, a bandeira, o final e a validade são guardados. */
        Cartao c = new Cartao(UUID.randomUUID().toString(), usuarioId, "tok_sim_" + UUID.randomUUID(), bandeira,
                digitos.substring(digitos.length() - 4), mes, ano, titular.toUpperCase(Locale.ROOT), CartaoSimulado.resultado(digitos), System.currentTimeMillis());
        c.setPadrao(cartoes.countByUsuarioId(usuarioId) == 0 || Boolean.TRUE.equals(n.padrao()));
        if (c.isPadrao()) cartoes.findByUsuarioIdOrderByCriadoEmAsc(usuarioId).forEach(x -> x.setPadrao(false));
        return CartaoDto.de(cartoes.save(c));
    }

    @Transactional
    public void removerCartao(Long usuarioId, String id) {
        Cartao c = cartoes.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Cartão não encontrado."));
        cartoes.delete(c);
        if (c.isPadrao()) cartoes.findByUsuarioIdOrderByCriadoEmAsc(usuarioId).stream().findFirst().ifPresent(x -> x.setPadrao(true));
    }

    @Transactional
    public void tornarPadrao(Long usuarioId, String id) {
        cartoes.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Cartão não encontrado."));
        cartoes.findByUsuarioIdOrderByCriadoEmAsc(usuarioId).forEach(x -> x.setPadrao(x.getId().equals(id)));
    }

    /* ---------------- pagar ---------------- */

    @Transactional
    public CobrancaDto pagar(String pedidoId, Long clienteId, String metodo, String cartaoId, Integer parcelas) {
        exigirAtivo();
        pedidos.findByIdForUpdate(pedidoId);
        Pedido p = pedidoDoCliente(pedidoId, clienteId);
        boolean inicial = "aguardando_pagamento".equals(p.getStatus());
        boolean complementar = Boolean.TRUE.equals(p.getExtra().get("pagamentoPendente"));
        if (!inicial && !complementar) throw erro(HttpStatus.CONFLICT, "Este pedido não tem nada a pagar.");
        BigDecimal devido = devido(p);
        if (devido.signum() <= 0) throw erro(HttpStatus.CONFLICT, "Este pedido já está pago.");
        /* Trocar de forma de pagamento: o Pix ainda em aberto é cancelado. */
        for (Cobranca c : doPedido(pedidoId)) {
            if (!Cobranca.PENDENTE.equals(c.getStatus())) continue;
            if ("cartao".equals(c.getMetodo()) && "asaas".equals(c.getProvedor()))
                throw erro(HttpStatus.CONFLICT, "Há um pagamento no cartão em conferência. Espere a confirmação antes de pagar de novo.");
            if (!cancelarNoProvedor(c)) {
                conferirPix(c);
                /* Pago agora há pouco: confirma em vez de recusar (lançar aqui desfaria a confirmação). */
                if (Cobranca.PAGA.equals(c.getStatus())) return DtoDe(c);
                throw erro(HttpStatus.CONFLICT, "O pagamento anterior ainda está em conferência. Atualize o pedido em instantes.");
            }
            c.cancelar("Substituída por outro pagamento.");
        }
        String tipo = inicial ? "principal" : "complementar";
        int n = parcelas == null ? 1 : parcelas;
        /* De R$ 1.000 a 2.999: até 12x; de 7x a 12x, os juros são do cliente e ficam fora do valor do serviço. */
        if ("cartao".equals(metodo) && n > 6) {
            if (devido.compareTo(MIL) < 0) throw erro(HttpStatus.BAD_REQUEST, "Abaixo de R$ 1.000, parcele em até 6x.");
            BigDecimal cobrado = comJuros(devido, n);
            jurosAtual.set(cobrado.subtract(devido));
            try { return DtoDe(cobrar(p, clienteId, metodo, cartaoId, n, cobrado, tipo)); } finally { jurosAtual.remove(); }
        }
        return DtoDe(cobrar(p, clienteId, metodo, cartaoId, n, devido, tipo));
    }

    /* Cria a cobrança na forma escolhida: cartão salvo, cartão novo (Asaas), Pix ou saldo da carteira TaskGO.
       Também usada para pagar pendências e para a troca de forma de pagamento (serviço em dinheiro). */
    Cobranca cobrar(Pedido p, Long pagadorId, String metodo, String cartaoId, int parcelas, BigDecimal valor, String tipo) {
        exigirAtivo();
        long agora = System.currentTimeMillis();
        if ("carteira".equals(metodo)) return cobrarCarteira(p, pagadorId, valor, tipo, agora);
        if ("cartao".equals(metodo)) {
            Cartao cartao = cartoes.findByIdAndUsuarioId(String.valueOf(cartaoId), pagadorId).orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Escolha um dos seus cartões."));
            if (asaasAtivo()) return cobrarCartaoAsaas(p, cartao, valor, tipo, parcelas, agora);
            return cobrarCartao(p, cartao, valor, tipo, parcelas, agora);
        }
        if (asaasAtivo()) return cobrarNoAsaas(p, pagadorId, metodo, valor, tipo, agora);
        if ("boleto".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Boleto só está disponível com o Asaas ligado.");
        if (!"pix".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Escolha Pix, cartão ou a carteira TaskGO.");
        Cobranca c = new Cobranca(UUID.randomUUID().toString(), p.getId(), pagadorId, "pix", tipo, valor, pix.nome(), agora);
        String txid = txid();
        ProvedorPix.CobrancaPix gerada;
        try { gerada = pix.criar(txid, valor, "TaskGO " + PedidoContratadoController.nomeServico(p.getService()), PIX_EXPIRA_SEGUNDOS); }
        catch (PixInterSimulado.ProvedorIndisponivel e) { throw erro(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
        c.pix(txid, gerada.copiaECola(), gerada.qrCode(), agora + PIX_EXPIRA_SEGUNDOS * 1000L);
        return cobrancas.save(c);
    }

    /* Mensalidade do Plano Pro: o mesmo caminho de cobrança (cartão salvo, página segura do Asaas ou Pix), sem pedido.
       A cobrança fica com a referência "plano:<assinatura>" e tipo "plano"; paga, quem cuida é o PlanoService. */
    Cobranca cobrarPlano(Long pagadorId, String metodo, String cartaoId, BigDecimal valor, String assinaturaId) {
        exigirAtivo();
        long agora = System.currentTimeMillis();
        String ref = "plano:" + assinaturaId, desc = "TaskGO · Plano Pro (1 mês)";
        if ("cartao".equals(metodo)) {
            Cartao cartao = cartoes.findByIdAndUsuarioId(String.valueOf(cartaoId), pagadorId).orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Escolha um dos seus cartões."));
            Cobranca c = new Cobranca(UUID.randomUUID().toString(), ref, pagadorId, "cartao", "plano", valor, asaasAtivo() ? "asaas" : "cartao-simulado", agora);
            c.cartao(cartao, 1);
            if (asaasAtivo()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode r = asaas.cobrarCartaoSalvo(clienteAsaas(pagadorId), cartao.getToken(), valor, 1, desc, c.getId(), null);
                    c.externa(r.path("id").asText(), r.path("invoiceUrl").asText(null));
                    resultadoCartao(c, r, agora);
                } catch (AsaasCliente.Erro e) { semRespostaOuRecusa(c, e); }
            } else if (cartao.vencido(YearMonth.now(FUSO))) c.recusar("Este cartão está vencido.");
            else { CartaoSimulado.Resposta r = CartaoSimulado.cobrar(cartao); if (r.aprovado()) c.pagar(agora); else c.recusar(r.motivo()); }
            cobrancas.save(c);
            if (Cobranca.PAGA.equals(c.getStatus())) planoPago(c);
            return c;
        }
        if (asaasAtivo()) {
            boolean pixPlano = "pix".equals(metodo);
            if (!pixPlano && !"cartao_novo".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Escolha Pix ou cartão.");
            Cobranca c = new Cobranca(UUID.randomUUID().toString(), ref, pagadorId, pixPlano ? "pix" : "cartao", "plano", valor, "asaas", agora);
            try {
                com.fasterxml.jackson.databind.JsonNode r = asaas.criarCobranca(clienteAsaas(pagadorId), pixPlano ? "PIX" : "CREDIT_CARD", valor, desc, c.getId());
                String id = r.path("id").asText();
                c.externa(id, r.path("invoiceUrl").asText(null));
                if (pixPlano) {
                    com.fasterxml.jackson.databind.JsonNode q = asaas.qrPix(id);
                    c.pix(id, q.path("payload").asText(), "data:image/png;base64," + q.path("encodedImage").asText(), agora + PIX_EXPIRA_SEGUNDOS * 1000L);
                }
            } catch (AsaasCliente.Erro e) { throw erro(e.status == 503 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST, e.getMessage()); }
            return cobrancas.save(c);
        }
        if (!"pix".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Escolha Pix ou um dos seus cartões.");
        Cobranca c = new Cobranca(UUID.randomUUID().toString(), ref, pagadorId, "pix", "plano", valor, pix.nome(), agora);
        String txid = txid();
        ProvedorPix.CobrancaPix gerada;
        try { gerada = pix.criar(txid, valor, "TaskGO Plano Pro", PIX_EXPIRA_SEGUNDOS); }
        catch (PixInterSimulado.ProvedorIndisponivel e) { throw erro(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
        c.pix(txid, gerada.copiaECola(), gerada.qrCode(), agora + PIX_EXPIRA_SEGUNDOS * 1000L);
        return cobrancas.save(c);
    }
    private void planoPago(Cobranca c) {
        if (planos != null) planos.cobrancaPaga(c);
        eventos.enviar(c.getClienteId(), "plano", null);
    }
    public CobrancaDto dto(Cobranca c) { return DtoDe(c); }

    /* Saldo da carteira TaskGO (troco online, ajudas recebidas): sai na hora, sem provedor. */
    private Cobranca cobrarCarteira(Pedido p, Long pagadorId, BigDecimal valor, String tipo, long agora) {
        Carteira carteira = carteiras.findByUsuarioIdForUpdate(pagadorId).orElse(null);
        BigDecimal saldo = carteira == null ? BigDecimal.ZERO : carteira.getSaldoDisponivel();
        if (carteira == null || saldo.compareTo(valor) < 0) throw erro(HttpStatus.CONFLICT, "Saldo da carteira insuficiente: você tem " + reais(saldo) + " e precisa de " + reais(valor) + ".");
        carteira.debitar(valor);
        carteiras.save(carteira);
        Cobranca c = new Cobranca(UUID.randomUUID().toString(), p.getId(), pagadorId, "carteira", tipo, valor, "carteira", agora);
        c.pagar(agora);
        cobrancas.save(c);
        aplicar(c, p);
        return c;
    }

    private Cobranca cobrarCartao(Pedido p, Cartao cartao, BigDecimal valor, String tipo, int parcelas, long agora) {
        int maximo = "etapa".equals(tipo) || valor.compareTo(MIL) >= 0 ? maxParcelasObra(valor) : maxParcelas(valor);
        if (parcelas < 1 || parcelas > maximo) throw erro(HttpStatus.BAD_REQUEST, "Escolha de 1 a " + maximo + " parcelas.");
        Cobranca c = new Cobranca(UUID.randomUUID().toString(), p.getId(), cartao.getUsuarioId(), "cartao", tipo, valor, "cartao-simulado", agora);
        c.juros(jurosAtual.get());
        c.cartao(cartao, parcelas);
        if (cartao.vencido(YearMonth.now(FUSO))) c.recusar("Este cartão está vencido.");
        else {
            CartaoSimulado.Resposta r = CartaoSimulado.cobrar(cartao);
            if (r.aprovado()) c.pagar(agora); else c.recusar(r.motivo());
        }
        cobrancas.save(c);
        if (Cobranca.PAGA.equals(c.getStatus())) aplicar(c, p);
        return c;
    }

    /* ---------------- Asaas ---------------- */

    /* Cliente no Asaas: criado na primeira cobrança e guardado na conta. */
    private String clienteAsaas(Long clienteId) {
        Usuario u = usuarios.findById(clienteId).orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Conta não encontrada."));
        if (u.getExtra().get("asaasCliente") instanceof String id && !id.isBlank()) return id;
        String cpf = u.getCpf() == null ? "" : u.getCpf().replaceAll("\\D", "");
        if (cpf.length() != 11) throw erro(HttpStatus.BAD_REQUEST, "Para pagar, informe seu CPF em Configurações.");
        String id;
        try { id = asaas.criarCliente(u.getNome(), cpf, u.getEmail(), u.getTelefone(), "conta-" + u.getId()); }
        catch (AsaasCliente.Erro e) { throw erro(HttpStatus.BAD_REQUEST, e.getMessage()); }
        u.getExtra().put("asaasCliente", id);
        usuarios.save(u);
        return id;
    }
    private String descricao(Pedido p) { return "TaskGO · " + PedidoContratadoController.nomeServico(p.getService()) + " · pedido " + p.getId(); }

    /* Pix (QR e copia-e-cola) ou cartão novo (página segura do Asaas). */
    private Cobranca cobrarNoAsaas(Pedido p, Long clienteId, String metodo, BigDecimal valor, String tipo, long agora) {
        boolean pixPedido = "pix".equals(metodo), boleto = "boleto".equals(metodo);
        if (!pixPedido && !boleto && !"cartao_novo".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Escolha Pix ou cartão.");
        Cobranca c = new Cobranca(UUID.randomUUID().toString(), p.getId(), clienteId, pixPedido ? "pix" : boleto ? "boleto" : "cartao", tipo, valor, "asaas", agora);
        try {
            com.fasterxml.jackson.databind.JsonNode r = asaas.criarCobranca(clienteAsaas(clienteId), pixPedido ? "PIX" : boleto ? "BOLETO" : "CREDIT_CARD", valor, descricao(p), c.getId());
            String id = r.path("id").asText();
            c.externa(id, r.path("invoiceUrl").asText(null));
            if (pixPedido) {
                com.fasterxml.jackson.databind.JsonNode q = asaas.qrPix(id);
                c.pix(id, q.path("payload").asText(), "data:image/png;base64," + q.path("encodedImage").asText(), agora + PIX_EXPIRA_SEGUNDOS * 1000L);
            }
        } catch (AsaasCliente.Erro e) { throw erro(e.status == 503 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST, e.getMessage()); }
        return cobrancas.save(c);
    }

    /* Cartão salvo (token do Asaas): cobra na hora, sem número de cartão. */
    private Cobranca cobrarCartaoAsaas(Pedido p, Cartao cartao, BigDecimal valor, String tipo, int parcelas, long agora) {
        int maximo = "etapa".equals(tipo) || valor.compareTo(MIL) >= 0 ? maxParcelasObra(valor) : maxParcelas(valor);
        if (parcelas < 1 || parcelas > maximo) throw erro(HttpStatus.BAD_REQUEST, "Escolha de 1 a " + maximo + " parcelas.");
        Cobranca c = new Cobranca(UUID.randomUUID().toString(), p.getId(), cartao.getUsuarioId(), "cartao", tipo, valor, "asaas", agora);
        c.juros(jurosAtual.get());
        c.cartao(cartao, parcelas);
        try {
            com.fasterxml.jackson.databind.JsonNode r = asaas.cobrarCartaoSalvo(clienteAsaas(cartao.getUsuarioId()), cartao.getToken(), valor, parcelas, descricao(p), c.getId(), null);
            c.externa(r.path("id").asText(), r.path("invoiceUrl").asText(null));
            resultadoCartao(c, r, agora);
        } catch (AsaasCliente.Erro e) { semRespostaOuRecusa(c, e); }
        cobrancas.save(c);
        if (Cobranca.PAGA.equals(c.getStatus())) aplicar(c, p);
        return c;
    }

    /* Depois do primeiro pagamento com cartão, guarda o token do Asaas para as próximas (só bandeira e final ficam visíveis). */
    private void guardarCartaoDoAsaas(Long clienteId, com.fasterxml.jackson.databind.JsonNode cc) {
        String token = cc.path("creditCardToken").asText("");
        if (token.isBlank() || cartoes.findByUsuarioIdOrderByCriadoEmAsc(clienteId).stream().anyMatch(x -> token.equals(x.getToken()))) return;
        if (cartoes.countByUsuarioId(clienteId) >= 5) return;
        String fim = cc.path("creditCardNumber").asText("").replaceAll("\\D", "");
        String bandeira = cc.path("creditCardBrand").asText("Cartão");
        bandeira = bandeira.isBlank() ? "Cartão" : bandeira.charAt(0) + bandeira.substring(1).toLowerCase(Locale.ROOT);
        Cartao c = new Cartao(UUID.randomUUID().toString(), clienteId, token, bandeira, fim.length() >= 4 ? fim.substring(fim.length() - 4) : "••••",
                12, 2099, usuarios.findById(clienteId).map(Usuario::getNome).orElse("").toUpperCase(Locale.ROOT), null, System.currentTimeMillis());
        c.setPadrao(cartoes.countByUsuarioId(clienteId) == 0);
        cartoes.save(c);
    }

    /* Cartão no Asaas: aprovado paga; em análise fica pendente (o aviso do Asaas ou a conferência confirma); o resto é recusa. */
    static void resultadoCartao(Cobranca c, com.fasterxml.jackson.databind.JsonNode r, long agora) {
        String st = r.path("status").asText();
        if ("PAGA".equals(AsaasCliente.situacao(st))) c.pagar(agora);
        else if (!AsaasCliente.emAnalise(st)) c.recusar("O cartão não foi aprovado. Tente outro cartão ou pague com Pix.");
    }
    /* Sem resposta (503) a cobrança pode ter sido criada: fica pendente para a conferência achar pelo nosso id. Recusa (4xx) é recusa. */
    static void semRespostaOuRecusa(Cobranca c, AsaasCliente.Erro e) { if (e.status != 503) c.recusar(e.getMessage()); }

    /* Cartões pendentes no Asaas (em análise ou sem resposta): confere a cada 2 minutos; depois de 48 h, cancela e libera. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 120_000, initialDelay = 90_000)
    public void conferirCartoesPendentes() {
        if (!asaasAtivo()) return;
        long agora = System.currentTimeMillis();
        /* Sem limite curto de idade: enquanto o Asaas não confirmar o cancelamento, continua tentando. */
        for (String id : cobrancas.findByMetodoAndStatusAndProvedorAndCriadaEmGreaterThan("cartao", Cobranca.PENDENTE, "asaas", agora - 365 * 86_400_000L).stream().map(Cobranca::getId).toList())
            porCobranca(id, () -> cobrancas.findById(id).filter(c -> Cobranca.PENDENTE.equals(c.getStatus())).ifPresent(c -> conferirCartao(c, agora)));
    }
    private void conferirCartao(Cobranca c, long agora) {
        {
                if (c.getTxid() == null) {
                    com.fasterxml.jackson.databind.JsonNode achada = asaas.porReferencia(c.getId()).path("data").path(0);
                    if (achada.isMissingNode() || achada.path("id").asText("").isBlank()) {
                        if (agora - c.getCriadaEm() > 30 * 60_000L) { c.recusar("O cartão não foi cobrado. Tente de novo."); cobrancas.save(c); }
                        return;
                    }
                    c.externa(achada.path("id").asText(), achada.path("invoiceUrl").asText(null)); cobrancas.save(c);
                }
                conferirPix(c);
                /* Em análise há mais de 48 h: cancela no Asaas e libera o pedido para outra forma. Se o cancelamento não for confirmado, espera. */
                if (Cobranca.PENDENTE.equals(c.getStatus()) && agora - c.getCriadaEm() > 48 * 3_600_000L && cancelarNoProvedor(c)) {
                    c.recusar("O cartão não foi confirmado em 48 horas. Pague de outra forma."); cobrancas.save(c);
                }
        }
    }

    /* Uma transação por cobrança: um erro não desfaz as confirmações das outras. */
    private org.springframework.transaction.support.TransactionTemplate tx, txNova;
    /* Cobranças de pendência, etapa, visita e semana: não são devolvidas pelo pedido (cada serviço devolve as suas). */
    private static final java.util.Set<String> TIPOS_FORA_DO_PEDIDO = java.util.Set.of("pendencia", "etapa", "visita", "semana");
    /* Marca no pedido: a devolução é de uma troca para dinheiro, então o pedido termina sem pagamento online (paid null). */
    public static final String ESTORNO_PARA_DINHEIRO = "estornoParaDinheiro";
    /* Referência da devolução: "estorno:<cobrança>:<desde>", onde "desde" é a hora da reserva. Liga a pendência à devolução feita. */
    private static final String PREFIXO_REF = "estorno:";
    @Autowired(required = false) void setTx(org.springframework.transaction.PlatformTransactionManager m) {
        this.tx = new org.springframework.transaction.support.TransactionTemplate(m);
        this.txNova = new org.springframework.transaction.support.TransactionTemplate(m);
        this.txNova.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /* Junta-se à transação de quem chamou ou abre uma. Exceção que sai daqui desfaz a transação compartilhada. */
    private <T> T emTransacao(java.util.function.Supplier<T> s) { return tx == null ? s.get() : tx.execute(st -> s.get()); }
    /* Transação própria (REQUIRES_NEW), com trava curta no Postgres. Também serve depois do commit de quem chamou (afterCommit). */
    private <T> T emNovaTransacao(java.util.function.Supplier<T> s) { return txNova == null ? s.get() : txNova.execute(st -> { travaCurta(); return s.get(); }); }
    /* Junta-se à transação de quem chamou, se houver; senão abre a sua. */
    private <T> T emTransacaoOuNova(java.util.function.Supplier<T> s) { return dentroDeTransacao() ? emTransacao(s) : emNovaTransacao(s); }
    private static boolean dentroDeTransacao() {
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive();
    }
    /* Só o Postgres tem lock_timeout (o H2 dos testes não). Espera no máximo 5 s por uma linha travada. */
    private Boolean postgres;
    private void travaCurta() {
        if (em == null) return;
        if (postgres == null) postgres = em.unwrap(org.hibernate.Session.class).doReturningWork(c -> "PostgreSQL".equals(c.getMetaData().getDatabaseProductName()));
        if (postgres) em.createNativeQuery("SET LOCAL lock_timeout = '5s'").executeUpdate();
    }
    private void porCobranca(String id, Runnable r) {
        try { if (tx == null) r.run(); else tx.executeWithoutResult(s -> r.run()); }
        catch (RuntimeException e) { log.warn("Conferência {} falhou: {}", id, e.getMessage()); }
    }

    /* true se a cobrança não pode mais ser paga no provedor (cancelada agora ou antes). */
    private boolean cancelarNoProvedor(Cobranca c) {
        /* Asaas sem id: a cobrança pode existir lá (resposta perdida). Só a conferência por referência decide. */
        if (c.getTxid() == null) return !"asaas".equals(c.getProvedor());
        if ("asaas".equals(c.getProvedor())) return asaas == null || asaas.cancelar(c.getTxid());
        return pix.cancelar(c.getTxid());
    }

    /* Aviso do Asaas (webhook): confere a cobrança direto na API dele. */
    @Transactional
    public void avisoAsaas(String idCobrancaAsaas) {
        if (idCobrancaAsaas != null) cobrancas.findByTxid(idCobrancaAsaas).ifPresent(this::conferirPix);
    }

    /* Sem juros, no máximo 6x e parcelas de pelo menos R$ 50. */
    static int maxParcelas(BigDecimal valor) { return Math.max(1, Math.min(6, valor.divide(BigDecimal.valueOf(50), 0, RoundingMode.DOWN).intValue())); }
    /* Etapa de obra: até 12x (de 7x a 12x com juros pagos pelo cliente), parcelas de pelo menos R$ 50. */
    static int maxParcelasObra(BigDecimal valor) { return Math.max(1, Math.min(12, valor.divide(BigDecimal.valueOf(50), 0, RoundingMode.DOWN).intValue())); }
    @Value("${taskgo.pagamentos.juros-mes:0.0249}") private BigDecimal jurosMes = new BigDecimal("0.0249");
    /* direto: o valor do serviço vai do cliente para quem atende; pela TaskGO passam só as taxas. */
    @Value("${taskgo.pagamentos.modelo:retido}") private String modelo = "retido";
    public boolean direto() { return "direto".equalsIgnoreCase(String.valueOf(modelo).trim()); }
    public String modelo() { return direto() ? "direto" : "retido"; }
    /** Total com juros (tabela Price) para 7x a 12x; até 6x, sem juros. */
    public BigDecimal comJuros(BigDecimal valor, int parcelas) {
        if (parcelas <= 6) return valor.setScale(2, RoundingMode.HALF_UP);
        double i = jurosMes.doubleValue(), fator = Math.pow(1 + i, parcelas);
        double parcela = valor.doubleValue() * i * fator / (fator - 1);
        return BigDecimal.valueOf(parcela).setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(parcelas)).setScale(2, RoundingMode.HALF_UP);
    }
    public BigDecimal jurosMes() { return jurosMes; }
    static final BigDecimal MIL = new BigDecimal("1000");
    private final ThreadLocal<BigDecimal> jurosAtual = new ThreadLocal<>();

    /* Pix do Plano Pro pendente: confere no provedor a cada 5 s, com a tela aberta ou não; sem depender do aviso do provedor.
       Pega também os vencidos (até 7 dias), para cancelar o vencido ou aplicar um pagamento feito no último instante, mesmo se o provedor ficou fora no vencimento.
       shortcut: uma consulta ao provedor por Pix pendente a cada 5 s; com muitos Pix abertos ao mesmo tempo, espaçar por idade. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5_000, initialDelay = 20_000)
    public void conferirPixDoPlano() {
        if (!ativo()) return;
        for (String id : cobrancas.findByTipoAndMetodoAndStatusAndExpiraEmGreaterThan("plano", "pix", Cobranca.PENDENTE, System.currentTimeMillis() - 7 * 86_400_000L).stream().map(Cobranca::getId).toList())
            porCobranca(id, () -> cobrancas.findById(id).ifPresent(this::conferirPix));
    }

    /* Pix: confere no provedor (o aviso só diz que algo mudou; a confirmação vem da consulta). */
    @Transactional
    public void avisoPix(List<String> txids) {
        for (String txid : txids) cobrancas.findByTxid(txid).ifPresent(this::conferirPix);
    }

    @Transactional
    public CobrancaDto consultar(String cobrancaId, Long clienteId) {
        Cobranca c = cobrancas.findByIdAndClienteId(cobrancaId, clienteId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pagamento não encontrado."));
        conferirPix(c);
        return DtoDe(c);
    }

    @Transactional
    public CobrancaDto simularPix(String cobrancaId, Long clienteId) {
        if (!teste()) throw erro(HttpStatus.FORBIDDEN, "Só existe no modo de teste.");
        Cobranca c = cobrancas.findByIdAndClienteId(cobrancaId, clienteId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pagamento não encontrado."));
        if (!Cobranca.PENDENTE.equals(c.getStatus())) throw erro(HttpStatus.CONFLICT, "Este pagamento não está mais esperando.");
        if ("asaas".equals(c.getProvedor())) {
            try { asaas.confirmarNoSandbox(c.getTxid()); } catch (AsaasCliente.Erro e) { throw erro(HttpStatus.BAD_GATEWAY, e.getMessage()); }
        } else {
            if (!"pix".equals(c.getMetodo())) throw erro(HttpStatus.CONFLICT, "Este Pix não está mais esperando pagamento.");
            try { pix.simularPagamento(c.getTxid()); } catch (PixInterSimulado.ProvedorIndisponivel e) { throw erro(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
        }
        conferirPix(c);
        return DtoDe(c);
    }

    private void conferirPix(Cobranca c) {
        if (!Cobranca.PENDENTE.equals(c.getStatus())) return;
        String s;
        if ("asaas".equals(c.getProvedor())) {
            if (asaas == null || c.getTxid() == null) return;
            com.fasterxml.jackson.databind.JsonNode r;
            try { r = asaas.cobranca(c.getTxid()); } catch (AsaasCliente.Erro e) { return; }
            s = AsaasCliente.situacao(r.path("status").asText());
            if ("PAGA".equals(s) && "cartao".equals(c.getMetodo()) && r.has("creditCard")) guardarCartaoDoAsaas(c.getClienteId(), r.path("creditCard"));
            if ("PENDENTE".equals(s) && ("cartao".equals(c.getMetodo()) || "boleto".equals(c.getMetodo()))) return; /* cartão ou boleto na página do Asaas: sem prazo de Pix */
        } else {
            if (!"pix".equals(c.getMetodo())) return;
            s = pix.status(c.getTxid());
        }
        long agora = System.currentTimeMillis();
        if ("PAGA".equals(s)) {
            /* Só uma confirmação aplica o pagamento (aviso do provedor e consulta da tela podem chegar juntos). */
            if (cobrancas.marcarPaga(c.getId(), Cobranca.PENDENTE, Cobranca.PAGA, agora) == 0) { if (em != null) em.refresh(c); return; }
            c.pagar(agora); cobrancas.save(c);
            /* Mensalidade do Plano Pro: não tem pedido. */
            if ("plano".equals(c.getTipo())) { planoPago(c); return; }
            pedidos.findById(c.getPedidoId()).ifPresent(p -> {
                /* Pedido cancelado enquanto o pagamento era confirmado: a devolução sai depois, fora desta transação (repetirDevolucoes). */
                if ("cancelado".equals(p.getStatus()) && !TIPOS_FORA_DO_PEDIDO.contains(c.getTipo())) { p.setPaid("estornando"); pedidos.save(p); return; }
                aplicar(c, p);
            });
        } else if ("CANCELADA".equals(s)) { c.cancelar("Pix cancelado."); cobrancas.save(c); }
        else if (c.getExpiraEm() != null && agora > c.getExpiraEm() && cancelarNoProvedor(c)) { c.cancelar("O Pix expirou. Gere outro."); cobrancas.save(c); }
    }

    /* Pagamento confirmado: o valor fica retido e o pedido segue. */
    private void aplicar(Cobranca c, Pedido p) {
        /* Pendência (serviço não pago, taxa de cancelamento, taxa de serviço em dinheiro): quem cuida é o DinheiroService. */
        if ("pendencia".equals(c.getTipo())) { if (dinheiro != null) dinheiro.pendenciaPaga(c); eventos.enviar(c.getClienteId(), "pagamento", p.getId()); return; }
        /* Obra: etapa e visita técnica têm regras próprias (ObraService). */
        if ("etapa".equals(c.getTipo()) || "visita".equals(c.getTipo())) { if (obras != null) obras.cobrancaPaga(c); eventos.enviar(c.getClienteId(), "pagamento", p.getId()); return; }
        /* Serviço recorrente: a semana paga segura as vezes daquela semana (RecorrenteService). */
        if ("semana".equals(c.getTipo())) { if (recorrentes != null) recorrentes.cobrancaPaga(c); eventos.enviar(c.getClienteId(), "pagamento", p.getId()); return; }
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "PAGAMENTO", c.getValor(), "RETIDO", p.getId(), System.currentTimeMillis()));
        auditar(c.getClienteId(), "Pagou " + reais(c.getValor()) + " com " + ("pix".equals(c.getMetodo()) ? "Pix" : "carteira".equals(c.getMetodo()) ? "o saldo da carteira" : c.getCartaoDescricao()) + ("complementar".equals(c.getTipo()) ? " (diferença)" : ""), c.getClienteId(), p.getId());
        p.setPaid("retido");
        if ("aguardando_pagamento".equals(p.getStatus())) {
            p.setStatus("aguardando");
            p.setCreated(System.currentTimeMillis());
            pedidos.save(p);
            publicacao.publicar(p);
        } else if (Boolean.TRUE.equals(p.getExtra().get("pagamentoPendente")) && devido(p).signum() <= 0) {
            Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.remove("pagamentoPendente"); p.setExtra(e);
            pedidos.save(p);
            boolean troca = "online".equals(p.getExtra().get("formaPagamento"));
            idDoEmail(p.getProEmail()).ifPresent(pro -> {
                eventos.enviar(pro, "pedido", p.getId());
                avisar(pro, p.getId(), troca ? "Pagamento online confirmado" : "Diferença paga", troca ? "O cliente trocou o dinheiro por pagamento online e já pagou. Não receba em dinheiro." : "O cliente pagou a diferença. Pode seguir para o atendimento.", "pagou-diferenca:" + c.getId());
            });
            /* Trocou de dinheiro para online depois de concluído: pagar já é a confirmação. */
            if ("concluido".equals(p.getStatus())) liberar(p);
        } else pedidos.save(p);
        eventos.enviar(c.getClienteId(), "pagamento", p.getId());
    }

    /* ---------------- depois do pagamento ---------------- */

    /* Proposta aceita com outro valor: cobra a diferença no cartão usado (se houver) ou pede ao cliente. */
    @Transactional
    public void aposMudarValor(Pedido p) {
        if (!ativo() || p.getPaid() == null) return;
        BigDecimal devido = devido(p);
        if (devido.signum() <= 0) return;
        Long cliente = clienteId(p);
        if (cliente == null) return;
        Cartao cartao = doPedido(p.getId()).stream()
                .filter(c -> Cobranca.PAGA.equals(c.getStatus()) && c.getCartaoId() != null).map(c -> cartoes.findById(c.getCartaoId()).orElse(null))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (cartao != null) {
            Cobranca c = cobrarCartao(p, cartao, devido, "complementar", 1, System.currentTimeMillis());
            if (Cobranca.PAGA.equals(c.getStatus())) {
                avisar(cliente, p.getId(), "Diferença cobrada", "Cobramos " + reais(devido) + " no " + c.getCartaoDescricao() + ", a diferença do valor combinado.", "diferenca:" + c.getId());
                return;
            }
        }
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put("pagamentoPendente", true); p.setExtra(e);
        pedidos.save(p);
        avisar(cliente, p.getId(), "Pague a diferença", "O valor combinado ficou " + reais(devido) + " maior. Pague a diferença para o profissional seguir.", "pagar-diferenca:" + p.getId());
        eventos.enviar(cliente, "pagamento", p.getId());
    }

    /* ---------------- conclusão: o cliente confirma (ou o prazo vence) ---------------- */
    static final long PRAZO_CONFIRMAR_MS = 12L * 60 * 60 * 1000;

    /* O profissional marcou "concluído": o valor continua retido até o cliente confirmar, abrir um problema ou o prazo vencer. */
    @Transactional
    public void aoConcluir(Pedido p) {
        if (!"retido".equals(p.getPaid())) return;
        long ate = System.currentTimeMillis() + PRAZO_CONFIRMAR_MS;
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra());
        e.put("liberacao", "aguardando"); e.put("liberarApos", ate);
        p.setExtra(e); pedidos.save(p);
        Long cliente = clienteId(p);
        avisar(cliente, p.getId(), "Confirme que o serviço terminou", "Se estiver tudo certo, confirme no pedido. Se tiver um problema, avise em até 12 horas; depois disso o pagamento vai para o profissional.", "confirmar-conclusao:" + p.getId());
        if (cliente != null) eventos.enviar(cliente, "pagamento", p.getId());
    }

    @Transactional
    public void confirmarConclusao(String pedidoId, Long clienteId) {
        Pedido p = pedidoConcluidoDoCliente(pedidoId, clienteId);
        liberar(p);
    }

    @Transactional
    public void abrirProblema(String pedidoId, Long clienteId, String motivo) {
        Pedido p = pedidoConcluidoDoCliente(pedidoId, clienteId);
        auditar(clienteId, "Relatou um problema depois da conclusão; pagamento retido", clienteId, pedidoId);
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 10 || texto.length() > 1000) throw erro(HttpStatus.BAD_REQUEST, "Conte o problema em 10 a 1000 caracteres.");
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra());
        e.put("liberacao", "problema"); e.put("problema", texto); e.put("problemaEm", System.currentTimeMillis());
        p.setExtra(e); pedidos.save(p);
        java.util.stream.Stream.of("suporte", "financeiro").flatMap(t -> usuarios.findByTipoIgnoreCaseOrderByNomeAsc(t).stream()).forEach(a -> {
            avisar(a.getId(), p.getId(), "Problema em um serviço concluído", "O cliente abriu um problema. O pagamento está retido até você decidir no detalhe do pedido.", "problema:" + p.getId() + ":" + a.getId());
            eventos.enviar(a.getId(), "notificacao", p.getId());
        });
        idDoEmail(p.getProEmail()).ifPresent(pro -> avisar(pro, p.getId(), "O cliente relatou um problema", "O pagamento fica retido até a equipe da TaskGO analisar.", "problema-pro:" + p.getId()));
    }

    /* Administrador decide um problema: libera ao profissional ou devolve ao cliente. */
    @Transactional
    public void decidirProblema(String pedidoId, boolean liberarAoProfissional) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!"retido".equals(p.getPaid()) || !"concluido".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Este pedido não tem pagamento retido para decidir.");
        if (liberarAoProfissional) { liberar(p); return; }
        estornar(p);
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.put("liberacao", "devolvido"); p.setExtra(e); pedidos.save(p);
    }

    /* Prazo vencido sem problema aberto: libera sozinho. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void liberarVencidos() {
        if (!ativo()) return;
        long agora = System.currentTimeMillis();
        for (Pedido p : pedidos.findByStatusAndPaid("concluido", "retido"))
            if ("aguardando".equals(p.getExtra().get("liberacao")) && p.getExtra().get("liberarApos") instanceof Number n && n.longValue() <= agora) liberar(p);
    }

    private Pedido pedidoConcluidoDoCliente(String pedidoId, Long clienteId) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!String.valueOf(clienteId).equals(p.getClientId())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        if (!"concluido".equals(p.getStatus()) || !"retido".equals(p.getPaid()) || !"aguardando".equals(p.getExtra().get("liberacao")))
            throw erro(HttpStatus.CONFLICT, "Este pagamento não está esperando confirmação.");
        return p;
    }

    /* Libera ao profissional o valor do serviço menos a taxa; a sobra volta ao cliente. */
    @Transactional
    public void liberar(Pedido p) {
        if (!"retido".equals(p.getPaid())) return;
        List<Cobranca> lista = doPedido(p.getId());
        BigDecimal pago = lista.stream().map(Cobranca::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal preco = BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal servico = pago.min(preco);
        BigDecimal valorTaxa = servico.multiply(taxaDe(p)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal repasse = servico.subtract(valorTaxa);
        long agora = System.currentTimeMillis();
        Long pro = idDoEmail(p.getProEmail()).orElse(null);
        if (pro != null && repasse.signum() > 0) {
            Carteira carteira = carteiras.findByUsuarioIdForUpdate(pro).orElseGet(() -> carteiras.save(new Carteira(pro)));
            carteira.creditar(repasse);
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), pro, "REPASSE", repasse, "CONCLUIDA", p.getId(), agora));
            if (dinheiro != null) dinheiro.descontarTaxas(pro);
            auditar(pro, "Recebeu " + reais(repasse) + " na carteira (taxa de " + reais(valorTaxa) + ")", null, p.getId());
            avisar(pro, p.getId(), "Pagamento liberado", reais(repasse) + " caíram na sua carteira (" + reais(servico) + " menos a taxa de " + reais(valorTaxa) + ").", "repasse:" + p.getId());
            eventos.enviar(pro, "pagamento", p.getId());
        }
        BigDecimal sobra = pago.subtract(servico);
        if (sobra.signum() > 0) devolver(lista, sobra, agora);
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra());
        e.put("repasse", repasse); e.put("taxa", valorTaxa); e.remove("pagamentoPendente"); e.put("liberacao", "liberado");
        p.setExtra(e); p.setPaid("liberado");
        pedidos.save(p);
    }

    /* Executor do Spring Boot: a devolução depois do commit roda em outra thread, para a conexão de quem chamou
       ser liberada antes do POST ao provedor (que pode levar até 15 s). Sem ele, fica para o job. */
    private org.springframework.core.task.TaskExecutor executor;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setExecutor(@org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor") org.springframework.core.task.TaskExecutor executor) { this.executor = executor; }

    /* Pedido cancelado (ou troca para dinheiro): devolve o que foi pago e cancela o Pix em aberto.
       A marca "estornando" sempre é gravada ANTES de devolver: se algo falhar no meio, o job (repetirDevolucoes) acha o pedido. */
    public void estornar(Pedido p) {
        if (!ativo()) return;
        String pedidoId = p.getId();
        if (!dentroDeTransacao()) {
            /* Quem chamou já gravou o cancelamento: a marca vai numa transação própria, antes de qualquer devolução. */
            /* O cancelamento já foi gravado: daqui para a frente nada sobe para o cliente. Se até a marca falhar, a rede do job
               (pedidos cancelados ainda com pagamento retido) acha o pedido. */
            try {
                emNovaTransacao(() -> { if (doPedido(pedidoId).stream().anyMatch(c -> c.liquido().signum() > 0)) marcarEstornando(pedidoId); return null; });
                processarEstorno(pedidoId);
            } catch (RuntimeException e) { log.warn("Devolução do pedido {} ficou para o job: {}", pedidoId, e.getMessage()); }
            return;
        }
        if (doPedido(pedidoId).stream().anyMatch(c -> c.liquido().signum() > 0)) { p.setPaid("estornando"); pedidos.save(p); }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() {
                Runnable devolver = () -> { try { processarEstorno(pedidoId); } catch (RuntimeException e) { log.warn("Devolução do pedido {} ficou para o job: {}", pedidoId, e.getMessage()); } };
                if (executor == null) { log.info("Devolução do pedido {} fica para o job", pedidoId); return; }
                try { executor.execute(devolver); } catch (RuntimeException e) { log.warn("Devolução do pedido {} ficou para o job: {}", pedidoId, e.getMessage()); }
            }
        });
    }

    /* Devolve o que sobrou pago de um pedido cancelado. Roda sem a transação de quem chamou: cada passo tem a sua,
       e o POST ao provedor nunca fica dentro de uma transação. Um erro numa cobrança não impede as outras; o que ficou
       é tentado de novo pelo job (repetirDevolucoes). */
    public void processarEstorno(String pedidoId) {
        if (!ativo()) return;
        for (Cobranca c : emNovaTransacao(() -> doPedido(pedidoId)))
            if (Cobranca.PENDENTE.equals(c.getStatus())) {
                try { emNovaTransacao(() -> { cobrancas.findById(c.getId()).filter(x -> Cobranca.PENDENTE.equals(x.getStatus())).ifPresent(x -> fecharAberta(x, "Pedido cancelado.")); return null; }); }
                catch (RuntimeException e) { log.warn("Cobrança {} do pedido {} não foi cancelada no provedor: {}", c.getId(), pedidoId, e.getMessage()); }
            }
        List<Cobranca> lista = emNovaTransacao(() -> doPedido(pedidoId));
        BigDecimal pago = lista.stream().map(Cobranca::liquido).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pago.signum() > 0) emNovaTransacao(() -> { marcarEstornando(pedidoId); return null; });
        boolean pendente = false;
        BigDecimal falta = pago;
        for (int i = lista.size() - 1; i >= 0 && falta.signum() > 0; i--) {
            Cobranca c = lista.get(i);
            BigDecimal livre = c.liquido();
            if (livre.signum() <= 0) continue;
            BigDecimal parte = livre.min(falta);
            falta = falta.subtract(parte);
            try { estornarParte(c, parte); }
            catch (RuntimeException e) { pendente = true; log.warn("Devolução de {} da cobrança {} ficou para depois: {}", reais(parte), c.getId(), e.getMessage()); }
        }
        if (pendente) {
            emNovaTransacao(() -> {
                pedidos.findById(pedidoId).ifPresent(p -> {
                    BigDecimal restante = doPedido(pedidoId).stream().map(Cobranca::liquido).reduce(BigDecimal.ZERO, BigDecimal::add);
                    avisar(clienteId(p), p.getId(), "Devolução em andamento", reais(restante) + " serão devolvidos assim que o banco liberar.", "estornando:" + p.getId());
                });
                return null;
            });
            return;
        }
        emNovaTransacao(() -> { pedidos.findById(pedidoId).filter(p -> "estornando".equals(p.getPaid())).ifPresent(p -> finalizarEstorno(p, doPedido(pedidoId))); return null; });
    }

    private void marcarEstornando(String pedidoId) {
        pedidos.findById(pedidoId).filter(p -> !"estornando".equals(p.getPaid())).ifPresent(p -> { p.setPaid("estornando"); pedidos.save(p); });
    }

    /* Uma parte de uma cobrança de pedido cancelado. Sempre fora de transação de quem chamou (ver processarEstorno). */
    private void estornarParte(Cobranca c, BigDecimal parte) {
        long agora = System.currentTimeMillis();
        if (!ehAsaas(c)) {
            emNovaTransacao(() -> { if (!devolverAqui(c, parte, agora, agora)) throw new EstornoAdiado("Esta devolução já está em andamento."); return null; });
            return;
        }
        if (emNovaTransacao(() -> cobrancas.reservarEstorno(c.getId(), parte, agora, Cobranca.PAGA)) == 0) throw new EstornoAdiado("Esta devolução já está em andamento.");
        enviarReservada(c, agora, parte, agora);
    }

    /* Pedido devolvido por inteiro: marca como estornado e avisa o cliente pelo que voltou. */
    private void finalizarEstorno(Pedido p, List<Cobranca> lista) {
        BigDecimal devolvido = lista.stream().map(Cobranca::getValorEstornado).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> e = new LinkedHashMap<>(p.getExtra()); e.remove("pagamentoPendente"); boolean paraDinheiro = e.remove(ESTORNO_PARA_DINHEIRO) != null; p.setExtra(e);
        p.setPaid(paraDinheiro ? null : "estornado");
        pedidos.save(p);
        Long cliente = clienteId(p);
        if (cliente != null && devolvido.signum() > 0) avisar(cliente, p.getId(), "Devolução enviada", "Enviamos a devolução de " + reais(devolvido) + " (" + lista.stream().filter(c -> c.getValorEstornado().signum() > 0).map(c -> "cartao".equals(c.getMetodo()) ? java.util.Objects.toString(c.getCartaoDescricao(), "cartão") : "Pix").distinct().reduce((a, b) -> a + " e " + b).orElse("") + "). No cartão, pode levar até 10 dias úteis para aparecer na fatura.", "estorno:" + p.getId());
    }

    private void devolver(List<Cobranca> lista, BigDecimal quanto, long agora) {
        BigDecimal falta = quanto;
        for (int i = lista.size() - 1; i >= 0 && falta.signum() > 0; i--) {
            Cobranca c = lista.get(i);
            BigDecimal livre = c.liquido();
            if (livre.signum() <= 0) continue;
            BigDecimal parte = livre.min(falta);
            try { estornarCobranca(c, parte, agora); }
            catch (EstornoAdiado adiado) { pendencia(c, parte, adiado); }
            falta = falta.subtract(parte);
        }
    }
    /* Devolução que o provedor ainda não aceitou: vira pendência (ObraService.prazos repete a cada minuto). */
    private void pendencia(Cobranca c, BigDecimal parte, EstornoAdiado adiado) {
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "DEVOLUCAO_PENDENTE", parte, "PENDENTE", adiado.referencia(c), System.currentTimeMillis()));
        avisar(c.getClienteId(), c.getPedidoId(), "Devolução em andamento", reais(parte) + " voltam para você assim que o banco liberar.", "devolucao-pendente:" + c.getId() + ":" + parte);
    }

    /* Devolve parte de uma cobrança. A parte é reservada antes (valor_estornando): outro processo não devolve de novo,
       e a reserva só some quando a devolução é confirmada ou recusada. */
    private void estornarCobranca(Cobranca c, BigDecimal parte, long agora) {
        if (ehAsaas(c)) { estornarNoAsaas(c, parte, agora); return; }
        if (!emTransacaoOuNova(() -> devolverAqui(c, parte, agora, agora))) throw new EstornoAdiado("Esta devolução já está em andamento.", referenciaReservada(c.getId()));
        if (em != null && em.contains(c)) em.refresh(c);
    }

    /* Carteira ou simulado: sem HTTP, reserva, crédito e confirmação ficam na mesma transação. Falso se a reserva não coube. */
    private boolean devolverAqui(Cobranca c, BigDecimal parte, long desde, long agora) {
        if (cobrancas.reservarEstorno(c.getId(), parte, desde, Cobranca.PAGA) == 0) return false;
        if ("carteira".equals(c.getProvedor())) {
            Carteira carteira = carteiras.findByUsuarioIdForUpdate(c.getClienteId()).orElseGet(() -> carteiras.save(new Carteira(c.getClienteId())));
            carteira.creditar(parte); carteiras.save(carteira);
        }
        concluirEstorno(c, parte, desde, agora);
        return true;
    }

    /* Asaas: o POST nunca sai com transação de quem chamou (a linha ficaria travada durante a chamada). Com transação, a reserva
       entra nela e o POST sai depois do commit; quem chamou recebe EstornoAdiado com a referência e registra "em andamento".
       Sem transação, reserva, POST e confirmação saem aqui. */
    private void estornarNoAsaas(Cobranca c, BigDecimal parte, long agora) {
        boolean dentro = dentroDeTransacao();
        long desde = System.currentTimeMillis();
        if (emTransacaoOuNova(() -> cobrancas.reservarEstorno(c.getId(), parte, desde, Cobranca.PAGA)) == 0) throw new EstornoAdiado("Esta devolução já está em andamento.", referenciaReservada(c.getId()));
        if (dentro) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { enviarDepoisDoCommit(c, desde, parte, agora); }
            });
            if (em != null && em.contains(c)) em.refresh(c);
            throw new EstornoAdiado("Devolução enviada depois da confirmação.", refEstorno(c.getId(), desde));
        }
        enviarReservada(c, desde, parte, agora);
        if (em != null && em.contains(c)) em.refresh(c);
    }

    private void enviarDepoisDoCommit(Cobranca c, long desde, BigDecimal parte, long agora) {
        try { enviarReservada(c, desde, parte, agora); }
        catch (RuntimeException e) { log.warn("Devolução da cobrança {} ficou para a conciliação: {}", c.getId(), e.getMessage()); }
    }

    /* POST ao Asaas com a reserva já gravada; a confirmação vai em transação própria. Recusa (4xx) solta a reserva;
       sem resposta (tempo esgotado, queda) mantém a reserva e a conciliação confere no Asaas. */
    private void enviarReservada(Cobranca c, long desde, BigDecimal parte, long agora) {
        try { asaas.estornar(c.getTxid(), parte, "Devolução TaskGO " + c.getId() + ":" + desde); }
        catch (AsaasCliente.Erro e) {
            if (e.status >= 400 && e.status < 500) emNovaTransacao(() -> cobrancas.liberarEstorno(c.getId(), desde));
            throw new EstornoAdiado("Não foi possível devolver agora: " + e.getMessage(), refEstorno(c.getId(), desde));
        }
        emNovaTransacao(() -> { concluirEstorno(c, parte, desde, agora); return null; });
    }

    private boolean ehAsaas(Cobranca c) { return "asaas".equals(c.getProvedor()) && asaas != null && c.getTxid() != null; }
    static String refEstorno(String cobrancaId, long desde) { return PREFIXO_REF + cobrancaId + ":" + desde; }
    static String cobrancaDaRef(String referencia) { return referencia.startsWith(PREFIXO_REF) ? referencia.substring(PREFIXO_REF.length(), referencia.lastIndexOf(':')) : referencia; }
    /* Reserva que já está aberta na cobrança (outro processo devolve): a pendência passa a esperar por ela. Null se não há. */
    private String referenciaReservada(String cobrancaId) {
        Cobranca c = cobrancas.findById(cobrancaId).orElse(null);
        if (c == null) return null;
        if (em != null && em.contains(c)) em.refresh(c);
        return c.getValorEstornando() == null || c.getEstornandoDesde() == null ? null : refEstorno(cobrancaId, c.getEstornandoDesde());
    }

    /* Devolução confirmada: soma na cobrança e registra o estorno (transação e auditoria). */
    private void concluirEstorno(Cobranca c, BigDecimal parte, long desde, long agora) {
        if (cobrancas.concluirEstorno(c.getId(), parte, agora, Cobranca.ESTORNADA) == 0) throw new IllegalStateException("Reserva da devolução da cobrança " + c.getId() + " não encontrada.");
        /* A chave da devolução vai no id, para retomarDevolucao achar (não há restrição no banco: quem impede gravar duas vezes é o UPDATE condicional de concluirEstorno); a referência continua o pedido,
           que é o que o extrato do cliente mostra. */
        transacoes.save(new TransacaoFinanceira(refEstorno(c.getId(), desde), c.getClienteId(), "ESTORNO", parte, "CONCLUIDA", c.getPedidoId(), agora));
        auditar(c.getClienteId(), "Recebeu de volta " + reais(parte), null, c.getPedidoId());
    }

    /* Devolução pedida ao Asaas sem resposta: depois de 10 minutos, confere no Asaas antes de qualquer nova tentativa. */
    private void conciliarEstornos(long agora) {
        for (String id : cobrancas.findByValorEstornandoIsNotNullAndEstornandoDesdeLessThan(agora - 10 * 60_000L).stream().map(Cobranca::getId).toList())
            porCobranca(id, () -> cobrancas.findById(id).ifPresent(c -> conciliarEstorno(c, agora)));
    }
    /* Procura a devolução pela marca que mandamos na descrição. Achou aceita: confirma. Não achou ou cancelada: solta a reserva e a próxima rodada tenta de novo.
       Qualquer outra coisa (valor diferente, mais de uma, consulta falhou) não resolve sozinha: fica para a equipe. */
    private void conciliarEstorno(Cobranca c, long agora) {
        if (c.getValorEstornando() == null || c.getEstornandoDesde() == null || c.getTxid() == null || asaas == null) return;
        long desde = c.getEstornandoDesde();
        BigDecimal parte = c.getValorEstornando();
        String marca = "Devolução TaskGO " + c.getId() + ":" + desde;
        com.fasterxml.jackson.databind.JsonNode lista;
        try { lista = asaas.listarEstornos(c.getTxid()); }
        catch (RuntimeException e) { divergencia(c, desde, "não foi possível consultar as devoluções no Asaas (" + e.getMessage() + ")"); return; }
        List<com.fasterxml.jackson.databind.JsonNode> achadas = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode r : lista.path("data")) if (r.path("description").asText("").contains(marca)) achadas.add(r);
        if (achadas.size() > 1 || lista.path("hasMore").asBoolean(false)) { divergencia(c, desde, "mais de uma devolução com esta marca, ou lista incompleta"); return; }
        String situacao = achadas.isEmpty() ? "" : achadas.get(0).path("status").asText("");
        if (achadas.isEmpty() || "CANCELLED".equals(situacao)) {
            /* Só solta para tentar de novo se o Asaas não devolveu nada além do que já está registrado aqui.
               Sem isso, uma descrição alterada pelo Asaas faria "não achei" e a próxima rodada devolveria em dobro. */
            BigDecimal noAsaas = BigDecimal.ZERO;
            for (com.fasterxml.jackson.databind.JsonNode r : lista.path("data"))
                if (!"CANCELLED".equals(r.path("status").asText("")) && r.path("value").isNumber()) noAsaas = noAsaas.add(r.path("value").decimalValue());
            BigDecimal registrado = c.getValorEstornado() == null ? BigDecimal.ZERO : c.getValorEstornado();
            if (noAsaas.compareTo(registrado) != 0) { divergencia(c, desde, "o Asaas tem devolução sem a nossa marca (" + reais(noAsaas) + " devolvidos lá, " + reais(registrado) + " aqui)"); return; }
            cobrancas.liberarEstorno(c.getId(), desde); return;
        }
        boolean aceita = "DONE".equals(situacao) || "PENDING".equals(situacao) || situacao.startsWith("AWAITING_");
        if (!aceita) { divergencia(c, desde, "situação desconhecida no Asaas: " + situacao); return; }
        com.fasterxml.jackson.databind.JsonNode valor = achadas.get(0).path("value");
        if (!valor.isNumber() || valor.decimalValue().compareTo(parte) != 0) { divergencia(c, desde, "valor diferente do reservado"); return; }
        /* O Asaas aceitou: mesma confirmação do caminho normal. */
        emNovaTransacao(() -> { concluirEstorno(c, parte, desde, agora); return null; });
        pedidos.findById(c.getPedidoId()).filter(p -> "cancelado".equals(p.getStatus()) && "retido".equals(p.getPaid())).ifPresent(p -> { p.setPaid("estornando"); pedidos.save(p); });
    }
    /* Não resolve sozinho: a reserva fica e a equipe confere no Asaas. A chave do aviso impede repetir a cada rodada. */
    private void divergencia(Cobranca c, long desde, String motivo) {
        log.error("Devolução da cobrança {} precisa de conferência: {}", c.getId(), motivo);
        java.util.stream.Stream.of("financeiro", "suporte").flatMap(t -> usuarios.findByTipoIgnoreCaseOrderByNomeAsc(t).stream())
                .forEach(a -> avisar(a.getId(), c.getPedidoId(), "Devolução para conferir", "Uma devolução ficou sem confirmação do Asaas. Confira no Asaas antes de devolver de novo.", "estorno-conferir:" + c.getId() + ":" + desde + ":" + a.getId()));
    }

    /* Provedor recusou ou ainda não confirmou a devolução; quem chama registra "em andamento". A referência é a reserva que ficou. */
    static final class EstornoAdiado extends ResponseStatusException {
        private final String referencia;
        EstornoAdiado(String motivo) { this(motivo, null); }
        EstornoAdiado(String motivo, String referencia) { super(HttpStatus.BAD_GATEWAY, motivo); this.referencia = referencia; }
        /* Sem reserva conhecida, a pendência fica com a cobrança (como antes). */
        String referencia(Cobranca c) { return referencia == null ? c.getId() : referencia; }
    }

    /* Devoluções que o provedor ainda não tinha aceitado: confere as reservas antigas e tenta de novo.
       Cada pedido em sua transação: um erro não desfaz as devoluções dos outros. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void repetirDevolucoes() {
        if (!ativo()) return;
        conciliarEstornos(System.currentTimeMillis());
        /* Rede de segurança: pedido cancelado que ainda tem dinheiro do próprio pedido retido (a marca "estornando" não chegou
           a ser gravada) entra na fila. Só marca; quem devolve é o laço abaixo. Etapas e semanas (obra, recorrente) ficam de
           fora, porque doPedido já as exclui e elas têm devolução própria. */
        for (String id : pedidos.findByStatusAndPaid("cancelado", "retido").stream().map(Pedido::getId).toList()) {
            try { emNovaTransacao(() -> { if (doPedido(id).stream().anyMatch(c -> c.liquido().signum() > 0)) marcarEstornando(id); return null; }); }
            catch (RuntimeException e) { log.warn("Pedido cancelado {} não entrou na fila de devolução: {}", id, e.getMessage()); }
        }
        for (String id : pedidos.findByPaid("estornando").stream().map(Pedido::getId).toList()) {
            try { processarEstorno(id); }
            catch (RuntimeException e) { log.warn("Devolução do pedido {} falhou: {}", id, e.getMessage()); }
        }
    }

    /* Pendência de devolução (DEVOLUCAO_PENDENTE, ObraService.prazos): devolve null se já está resolvida; senão, a referência
       que a pendência passa a ter (a da reserva nova ou a que ficou). Não repete devolução que o provedor já aceitou. */
    public String retomarDevolucao(String referencia, BigDecimal valor) {
        Cobranca c = cobrancas.findById(cobrancaDaRef(referencia)).orElse(null);
        if (c == null) { log.warn("Devolução pendente {} sem cobrança: confira no banco", referencia); return referencia; }
        if (!referencia.startsWith(PREFIXO_REF)) return devolverDeNovo(c, valor);
        if (transacoes.existsById(referencia)) return null;
        long desde = Long.parseLong(referencia.substring(referencia.lastIndexOf(':') + 1));
        /* Reserva aberta com esta data: a conciliação confere no Asaas e decide. */
        if (c.getValorEstornando() != null && c.getEstornandoDesde() != null && c.getEstornandoDesde() == desde) return referencia;
        if (Cobranca.ESTORNADA.equals(c.getStatus())) return null;
        return devolverDeNovo(c, valor);
    }
    private String devolverDeNovo(Cobranca c, BigDecimal valor) {
        try { devolverCobranca(c, valor); return null; }
        catch (EstornoAdiado adiado) { return adiado.referencia(c); }
    }

    /* ---------------- consulta ---------------- */
    /* ---------------- consulta ---------------- */

    @Transactional
    public ResumoPagamento resumo(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        boolean cliente = String.valueOf(quem.id()).equals(p.getClientId());
        boolean pro = quem.email().equalsIgnoreCase(String.valueOf(p.getProEmail()));
        if (!cliente && !pro) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        List<Cobranca> lista = doPedido(pedidoId);
        if (cliente) lista.forEach(this::conferirPix);
        BigDecimal pago = lista.stream().map(Cobranca::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estornado = lista.stream().map(Cobranca::getValorEstornado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal preco = BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorTaxa = preco.multiply(taxaDe(p)).setScale(2, RoundingMode.HALF_UP);
        Cobranca aberta = lista.stream().filter(c -> Cobranca.PENDENTE.equals(c.getStatus())).reduce((a, b) -> b).orElse(null);
        Cobranca ultima = lista.isEmpty() ? null : lista.get(lista.size() - 1);
        return new ResumoPagamento(modo, p.getPaid(), p.getExtra().get("liberacao") instanceof String l ? l : null,
                p.getExtra().get("liberarApos") instanceof Number n ? n.longValue() : null, preco, pago, estornado, devido(p).max(BigDecimal.ZERO), valorTaxa, preco.subtract(valorTaxa),
                devido(p).compareTo(MIL) >= 0 ? maxParcelasObra(devido(p)) : maxParcelas(devido(p).max(BigDecimal.ONE)),
                cliente ? lista.stream().map(this::DtoDe).toList() : List.of(),
                cliente && aberta != null ? DtoDe(aberta) : null,
                cliente && ultima != null && Cobranca.RECUSADA.equals(ultima.getStatus()) ? ultima.getMotivo() : null);
    }

    BigDecimal devido(Pedido p) {
        BigDecimal preco = BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pago = doPedido(p.getId()).stream().map(Cobranca::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        return preco.subtract(pago);
    }

    /* ---------------- apoio ---------------- */

    private Pedido pedidoDoCliente(String pedidoId, Long clienteId) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!String.valueOf(clienteId).equals(p.getClientId())) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        if ("cancelado".equals(p.getStatus()) || "concluido".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Este pedido já foi encerrado.");
        return p;
    }
    private Long clienteId(Pedido p) { try { return Long.valueOf(p.getClientId()); } catch (RuntimeException e) { return null; } }
    private java.util.Optional<Long> idDoEmail(String email) { return email == null ? java.util.Optional.empty() : usuarios.findByEmailIgnoreCase(email).map(Usuario::getId); }
    private void exigirAtivo() { if (!ativo()) throw erro(HttpStatus.CONFLICT, "O pagamento está desligado."); }
    private static String txid() {
        String abc = "abcdefghijklmnopqrstuvwxyz0123456789"; StringBuilder s = new StringBuilder("taskgo");
        while (s.length() < 32) s.append(abc.charAt(ALEATORIO.nextInt(abc.length())));
        return s.toString();
    }
    static String reais(BigDecimal v) { return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", v); }
    private void avisar(Long usuarioId, String pedidoId, String titulo, String texto, String chave) {
        if (notificacoes == null || usuarioId == null) return;
        try { notificacoes.criar(usuarioId, pedidoId, "pedido", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
    private static ResponseStatusException erro(HttpStatus s, String m) { return new ResponseStatusException(s, m); }

    private CobrancaDto DtoDe(Cobranca c) { return CobrancaDto.de(c); }
    /* Cobranças do próprio pedido; as de pendência (dívida paga depois) ficam fora das contas do pedido. */
    private List<Cobranca> doPedido(String pedidoId) { return cobrancas.findByPedidoIdOrderByCriadaEmAsc(pedidoId).stream().filter(c -> !TIPOS_FORA_DO_PEDIDO.contains(c.getTipo())).toList(); }

    public record NovoCartao(String numero, String titular, Integer mes, Integer ano, String cvv, Boolean padrao) {}
    public record CartaoDto(String id, String bandeira, String final4, int mes, int ano, String titular, boolean padrao, boolean vencido) {
        static CartaoDto de(Cartao c) { return new CartaoDto(c.getId(), c.getBandeira(), c.getFinal4(), c.getMesValidade(), c.getAnoValidade(), c.getTitular(), c.isPadrao(), c.vencido(YearMonth.now(FUSO))); }
    }
    public record CobrancaDto(String id, String pedidoId, String metodo, String tipo, BigDecimal valor, String status, String motivo,
                              String pixCopiaECola, String qrCode, Long expiraEm, String cartao, Integer parcelas, BigDecimal valorEstornado, long criadaEm, Long pagaEm, String link) {
        static CobrancaDto de(Cobranca c) {
            boolean aberto = Cobranca.PENDENTE.equals(c.getStatus());
            return new CobrancaDto(c.getId(), c.getPedidoId(), c.getMetodo(), c.getTipo(), c.getValor(), c.getStatus(), c.getMotivo(),
                    aberto ? c.getPixCopiaECola() : null, aberto ? c.getQrCode() : null, c.getExpiraEm(), c.getCartaoDescricao(), c.getParcelas(),
                    c.getValorEstornado(), c.getCriadaEm(), c.getPagaEm(), aberto ? c.getLinkPagamento() : null);
        }
    }
    public record ResumoPagamento(String modo, String situacao, String liberacao, Long liberarApos, BigDecimal valor, BigDecimal pago, BigDecimal devolvido, BigDecimal falta,
                                  BigDecimal taxa, BigDecimal repasse, int maxParcelas, List<CobrancaDto> cobrancas, CobrancaDto aberta, String recusa) {}

    static List<String> txidsDoAviso(Object corpo) {
        List<String> r = new ArrayList<>();
        if (corpo instanceof Map<?, ?> m && m.get("pix") instanceof List<?> l)
            for (Object x : l) if (x instanceof Map<?, ?> item && item.get("txid") instanceof String t && t.length() <= 40) r.add(t);
        return r;
    }
}
