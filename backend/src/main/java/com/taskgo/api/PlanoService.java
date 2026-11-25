package com.taskgo.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/* Plano Pro do profissional: R$ 29,90 por mês, no cartão (renova sozinho) ou no Pix (paga a cada mês).
   Enquanto vale: taxa de 10% nos pedidos comuns e no recorrente, destaque na lista com selo Pro,
   uma onda antes no Chamar agora e até 20 categorias. Cancelar só para a renovação: vale até o fim do mês pago. */
@Service
public class PlanoService {
    private static final Logger log = LoggerFactory.getLogger(PlanoService.class);
    public static final BigDecimal PRECO = new BigDecimal("29.90");
    public static final BigDecimal TAXA_PRO = new BigDecimal("0.10");
    public static final int CATEGORIAS = 10, CATEGORIAS_PRO = 20;
    static final long DIA = 86_400_000L;
    /* Cartão: tenta no último dia e por mais 3 dias depois, uma vez por dia. Pix: avisa 3 dias antes; pode pagar 7 dias antes. */
    static final int DIAS_TOLERANCIA = 3, DIAS_AVISO_PIX = 3, DIAS_PAGAR_ANTES = 7;
    static final ZoneId FUSO = ZoneId.of("America/Recife");
    static final String REF = "plano:";

    private final AssinaturaRepository assinaturas;
    private final UsuarioRepository usuarios;
    private final PagamentoService pagamentos;
    private final CobrancaRepository cobrancas;
    private final CartaoRepository cartoes;

    public PlanoService(AssinaturaRepository assinaturas, UsuarioRepository usuarios, PagamentoService pagamentos,
                        CobrancaRepository cobrancas, CartaoRepository cartoes) {
        this.assinaturas = assinaturas; this.usuarios = usuarios; this.pagamentos = pagamentos; this.cobrancas = cobrancas; this.cartoes = cartoes;
    }
    private NotificacaoService notificacoes;
    @Autowired(required = false) void setNotificacoes(NotificacaoService n) { this.notificacoes = n; }
    private ModeracaoService moderacao;
    @Autowired(required = false) void setModeracao(ModeracaoService m) { this.moderacao = m; }
    private ServicosProfissional servicos;
    @Autowired(required = false) void setServicos(@org.springframework.context.annotation.Lazy ServicosProfissional s) { this.servicos = s; }

    /* ---------------- quem é Pro ---------------- */

    public boolean ehPro(Long usuarioId) {
        if (usuarioId == null) return false;
        long agora = System.currentTimeMillis();
        return assinaturas.findByUsuarioId(usuarioId).map(a -> a.vale(agora)).orElse(false);
    }
    public boolean ehProEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return usuarios.findByEmailIgnoreCase(email.trim()).map(u -> ehPro(u.getId())).orElse(false);
    }
    /** Entre as contas dadas, as que estão com o Plano Pro valendo agora. */
    public Set<Long> pros(Collection<Long> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) return Set.of();
        long agora = System.currentTimeMillis();
        return assinaturas.findByUsuarioIdIn(usuarioIds).stream().filter(a -> a.vale(agora)).map(Assinatura::getUsuarioId).collect(Collectors.toSet());
    }
    public int maxCategorias(Usuario u) { return u != null && ehPro(u.getId()) ? CATEGORIAS_PRO : CATEGORIAS; }

    /* ---------------- situação (tela do plano) ---------------- */

    public Map<String, Object> situacao(Usuario u) {
        long agora = System.currentTimeMillis();
        Assinatura a = assinaturas.findByUsuarioId(u.getId()).orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("disponivel", "profissional".equalsIgnoreCase(u.getTipo()));
        r.put("preco", PRECO);
        r.put("taxaPro", 10); r.put("categoriasPro", CATEGORIAS_PRO);
        r.put("pro", a != null && a.vale(agora));
        r.put("status", a == null ? null : a.getStatus());
        r.put("forma", a == null ? null : a.getForma());
        r.put("cartaoId", a == null ? null : a.getCartaoId());
        r.put("cartao", a == null || a.getCartaoId() == null ? null : cartoes.findByIdAndUsuarioId(a.getCartaoId(), u.getId()).map(Cartao::descricao).orElse(null));
        r.put("pagoAte", a == null ? null : a.getPagoAte());
        r.put("podePagar", a == null || !a.vale(agora) || a.getPagoAte() - agora <= DIAS_PAGAR_ANTES * DIA);
        r.put("cobranca", a == null ? null : cobrancaAberta(a).map(pagamentos::dto).orElse(null));
        Long prazo = a == null || !desistivel(a) ? null : prazoDesistencia(a.getContratadaEm());
        /* Último instante aceito (23:59:59 do 7º dia): a tela mostra este dia, não o seguinte. */
        r.put("desistirAte", prazo != null && agora < prazo ? prazo - 1 : null);
        r.put("desistidaEm", a == null ? null : a.getDesistidaEm());
        return r;
    }
    private Optional<Cobranca> cobrancaAberta(Assinatura a) {
        long agora = System.currentTimeMillis();
        return cobrancas.findByPedidoIdOrderByCriadaEmAsc(REF + a.getId()).stream()
                .filter(c -> Cobranca.PENDENTE.equals(c.getStatus()) && (c.getExpiraEm() == null || c.getExpiraEm() > agora))
                /* Pix que o provedor não deixou cancelar na desistência: não oferece para pagar. */
                .filter(c -> a.getDesistidaEm() == null || c.getCriadaEm() > a.getDesistidaEm())
                .reduce((x, y) -> y);
    }

    /* ---------------- assinar, pagar, cancelar ---------------- */

    /** Assina (ou volta a assinar). forma: cartao | pix (como renova). metodo do 1º pagamento: cartao (salvo), cartao_novo (página segura) ou pix. */
    public Map<String, Object> assinar(Long usuarioId, String forma, String metodo, String cartaoId) {
        Usuario u = profissional(usuarioId);
        String f = validarForma(forma, metodo, cartaoId, usuarioId);
        long agora = System.currentTimeMillis();
        Assinatura a = assinaturas.findByUsuarioId(usuarioId).orElseGet(() -> new Assinatura(UUID.randomUUID().toString(), usuarioId, f, null, PRECO, agora));
        if (a.vale(agora) && Assinatura.ATIVA.equals(a.getStatus())) throw erro(HttpStatus.CONFLICT, "Seu Plano Pro já está ativo.");
        a.forma(f, "cartao".equals(metodo) ? cartaoId : null);
        /* Cancelou mas o mês ainda vale: volta a renovar, sem cobrar agora. */
        if (a.vale(agora)) {
            a.ativar(); assinaturas.save(a);
            auditar(usuarioId, "Voltou a renovar o Plano Pro");
            return Map.of("situacao", situacao(u));
        }
        assinaturas.save(a);
        return cobrarMes(u, a, metodo, cartaoId);
    }

    /** Paga um mês (Pix a cada mês, ou cartão quando a renovação automática falhou). */
    public Map<String, Object> pagar(Long usuarioId, String metodo, String cartaoId) {
        Usuario u = profissional(usuarioId);
        long agora = System.currentTimeMillis();
        Assinatura a = assinaturas.findByUsuarioId(usuarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Assine o Plano Pro primeiro."));
        if (a.vale(agora) && a.getPagoAte() - agora > DIAS_PAGAR_ANTES * DIA)
            throw erro(HttpStatus.CONFLICT, "Seu Plano Pro já está pago até " + data(a.getPagoAte()) + ". O próximo mês pode ser pago a partir de " + data(a.getPagoAte() - DIAS_PAGAR_ANTES * DIA) + ".");
        validarForma(a.getForma(), metodo, cartaoId, usuarioId);
        return cobrarMes(u, a, metodo, cartaoId);
    }

    private Map<String, Object> cobrarMes(Usuario u, Assinatura a, String metodo, String cartaoId) {
        /* Pix ainda válido do mesmo plano: devolve o mesmo, sem gerar outro. */
        if ("pix".equals(metodo)) {
            Optional<Cobranca> aberta = cobrancaAberta(a).filter(c -> "pix".equals(c.getMetodo()));
            if (aberta.isPresent()) return Map.of("situacao", situacao(u), "cobranca", pagamentos.dto(aberta.get()));
        }
        Cobranca c = pagamentos.cobrarPlano(u.getId(), metodo, cartaoId, a.getValor(), a.getId());
        if (Cobranca.RECUSADA.equals(c.getStatus())) throw erro(HttpStatus.PAYMENT_REQUIRED, c.getMotivo() == null ? "O cartão não foi aprovado." : c.getMotivo());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("situacao", situacao(u));
        r.put("cobranca", pagamentos.dto(c));
        return r;
    }

    public Map<String, Object> cancelar(Long usuarioId) {
        Usuario u = profissional(usuarioId);
        Assinatura a = assinaturas.findByUsuarioId(usuarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Você não tem Plano Pro."));
        if (Assinatura.CANCELADA.equals(a.getStatus())) throw erro(HttpStatus.CONFLICT, "A renovação já está cancelada.");
        long agora = System.currentTimeMillis();
        a.cancelar(agora); assinaturas.save(a);
        auditar(usuarioId, "Cancelou a renovação do Plano Pro");
        if (a.vale(agora)) avisar(usuarioId, "Renovação do Plano Pro cancelada", "Seus benefícios continuam até " + data(a.getPagoAte()) + ". Você pode voltar a renovar quando quiser.", "plano-cancelado:" + agora);
        return Map.of("situacao", situacao(u));
    }

    /** Desiste da contratação nova em até 7 dias, com devolução integral (CDC art. 49). */
    public Map<String, Object> desistir(Long usuarioId) { return desistir(usuarioId, System.currentTimeMillis()); }
    Map<String, Object> desistir(Long usuarioId, long agora) {
        Usuario u = profissional(usuarioId);
        Assinatura a = assinaturas.findByUsuarioId(usuarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Você não tem Plano Pro."));
        if (a.getContratadaEm() != null && !desistivel(a)) throw erro(HttpStatus.CONFLICT, "Você já desistiu desta contratação do Plano Pro.");
        if (!desistivel(a)) throw erro(HttpStatus.BAD_REQUEST, "Não há Plano Pro contratado para desistir.");
        long prazo = prazoDesistencia(a.getContratadaEm());
        if (agora >= prazo) throw erro(HttpStatus.BAD_REQUEST, "O prazo de 7 dias para desistir terminou em " + data(prazo - 1) + ".");
        a.desistir(agora);
        try { assinaturas.save(a); }
        catch (OptimisticLockingFailureException e) { throw erro(HttpStatus.CONFLICT, "Seu plano mudou agora. Atualize a tela e tente de novo."); }
        cobrancas.findByPedidoIdOrderByCriadaEmAsc(REF + a.getId()).stream()
                .filter(c -> Cobranca.PENDENTE.equals(c.getStatus())).forEach(c -> pagamentos.cancelarAberta(c, "Plano Pro desistido."));
        boolean adiada = devolverPagas(a);
        auditar(usuarioId, "Desistiu do Plano Pro e pediu a devolução");
        String aviso = adiada ? "Devolução em andamento: o banco ainda não confirmou. Vamos tentar de novo." : "Você desistiu do Plano Pro e o valor pago foi devolvido.";
        avisar(usuarioId, "Desistência do Plano Pro", aviso, "plano-desistencia:" + a.getId() + ":" + agora);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("situacao", situacao(u));
        r.put("aviso", aviso);
        return r;
    }
    /* Devolve o que foi pago nesta contratação. Devolve true se o provedor adiou alguma (o cuidar tenta de novo). */
    private boolean devolverPagas(Assinatura a) {
        boolean adiada = false;
        for (Cobranca c : cobrancas.findByPedidoIdOrderByCriadaEmAsc(REF + a.getId())) {
            if (c.getPagaEm() == null || c.getPagaEm() < a.getContratadaEm() || c.liquido().signum() <= 0) continue;
            try { pagamentos.devolverCobranca(c, c.liquido()); }
            catch (PagamentoService.EstornoAdiado e) { adiada = true; }
            /* A desistência já está gravada: falha inesperada vira "em andamento" e o cuidar tenta de novo. */
            catch (RuntimeException e) { adiada = true; log.error("Plano Pro {}: falha ao devolver a cobrança {}", a.getId(), c.getId(), e); }
        }
        return adiada;
    }
    /* Só a contratação atual conta: uma desistência anterior não impede desistir de uma nova. */
    static boolean desistivel(Assinatura a) { return a.getContratadaEm() != null && (a.getDesistidaEm() == null || a.getDesistidaEm() < a.getContratadaEm()); }
    /* 7 dias a partir do dia da contratação (o dia do início não conta, art. 132 do Código Civil). */
    static long prazoDesistencia(long contratadaEm) {
        return Instant.ofEpochMilli(contratadaEm).atZone(FUSO).toLocalDate().atStartOfDay(FUSO).toInstant().toEpochMilli() + 8 * DIA;
    }

    /** Troca como o plano renova (cartão salvo ou Pix). */
    public Map<String, Object> trocarForma(Long usuarioId, String forma, String cartaoId) {
        Usuario u = profissional(usuarioId);
        Assinatura a = assinaturas.findByUsuarioId(usuarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Assine o Plano Pro primeiro."));
        String f = validarForma(forma, Assinatura.CARTAO.equals(forma) ? "cartao" : "pix", cartaoId, usuarioId);
        a.forma(f, Assinatura.CARTAO.equals(f) ? cartaoId : null);
        assinaturas.save(a);
        auditar(usuarioId, Assinatura.CARTAO.equals(f) ? "Plano Pro passa a renovar no cartão" : "Plano Pro passa a renovar por Pix");
        return Map.of("situacao", situacao(u));
    }

    /** Chamado pelo PagamentoService quando a mensalidade é confirmada: estende um mês. */
    public void cobrancaPaga(Cobranca c) {
        if (c == null || c.getPedidoId() == null || !c.getPedidoId().startsWith(REF)) return;
        Assinatura a = assinaturas.findById(c.getPedidoId().substring(REF.length())).orElse(null);
        if (a == null) return;
        long agora = System.currentTimeMillis();
        long pagoEm = c.getPagaEm() != null ? c.getPagaEm() : agora;
        /* Pagou depois de desistir (Pix que não foi cancelado a tempo): devolve e não estende o plano. */
        if (a.getDesistidaEm() != null && c.getCriadaEm() <= a.getDesistidaEm()) {
            boolean adiada = false;
            try { pagamentos.devolverCobranca(c, c.liquido()); }
            catch (RuntimeException e) { adiada = true; log.warn("Plano Pro {}: devolução tardia falhou, tenta de novo no cuidar", a.getId(), e); }
            avisar(a.getUsuarioId(), "Pagamento do Plano Pro devolvido", adiada
                    ? "Chegou um pagamento depois que você desistiu do Plano Pro. A devolução está em andamento: o banco ainda não confirmou."
                    : "Chegou um pagamento depois que você desistiu do Plano Pro. O valor foi devolvido.", "plano-devolucao-tardia:" + c.getId());
            return;
        }
        boolean renovacao = a.vale(agora);
        /* Contratação nova (1º pagamento, ou voltar depois de ficar sem plano) começa a janela de desistência. */
        if (a.getPagoAte() == null || (!renovacao && !Assinatura.ATIVA.equals(a.getStatus()))) a.contratou(pagoEm);
        long ate = proximoMes(renovacao ? a.getPagoAte() : agora);
        /* Cartão: renova no cartão que pagou (o da página segura fica salvo depois do 1º pagamento). */
        if (Assinatura.CARTAO.equals(a.getForma())) {
            String cartao = c.getCartaoId() != null ? c.getCartaoId() : ultimoCartao(a.getUsuarioId());
            if (cartao != null) a.cartao(cartao);
        }
        a.pagoAte(ate);
        assinaturas.save(a);
        auditar(a.getUsuarioId(), "Pagou o Plano Pro (" + PagamentoService.reais(c.getValor()) + "), vale até " + data(ate));
        avisar(a.getUsuarioId(), renovacao ? "Plano Pro renovado" : "Plano Pro ativo",
                "Seus benefícios valem até " + data(ate) + ": taxa de 10%, destaque na lista, chamado antes e até 20 categorias.", "plano-pago:" + c.getId());
    }
    private String ultimoCartao(Long usuarioId) {
        List<Cartao> l = cartoes.findByUsuarioIdOrderByCriadoEmAsc(usuarioId);
        return l.isEmpty() ? null : l.get(l.size() - 1).getId();
    }

    /* ---------------- renovação mensal ---------------- */

    @Scheduled(fixedDelay = 30 * 60_000L, initialDelay = 120_000L)
    public void renovar() {
        long agora = System.currentTimeMillis();
        for (Assinatura a : assinaturas.findByStatusIn(List.of(Assinatura.ATIVA, Assinatura.CANCELADA))) {
            try { cuidar(a, agora); } catch (RuntimeException e) { log.warn("Plano Pro {}: {}", a.getId(), e.getMessage()); }
        }
    }

    void cuidar(Assinatura a, long agora) {
        Long ate = a.getPagoAte();
        if (ate == null) return;
        /* shortcut: espera 10 min para não disputar com o pedido HTTP; com volume, trocar por marcação atômica na cobrança */
        if (a.getDesistidaEm() != null && a.getContratadaEm() != null && a.getDesistidaEm() >= a.getContratadaEm() && agora - a.getDesistidaEm() > 10 * 60_000L)
            devolverPagas(a);
        if (Assinatura.ATIVA.equals(a.getStatus())) {
            Decisao d = decidir(a.getForma(), a.getCartaoId() != null, ate, a.getUltimaTentativa(), a.getAvisoEnviado(), agora);
            if (d == Decisao.COBRAR_CARTAO && pagamentos.ativo()) {
                a.tentou(agora); assinaturas.save(a);
                Cobranca c;
                try { c = pagamentos.cobrarPlano(a.getUsuarioId(), "cartao", a.getCartaoId(), a.getValor(), a.getId()); }
                catch (RuntimeException e) { c = null; }
                if (c == null || Cobranca.RECUSADA.equals(c.getStatus()))
                    avisar(a.getUsuarioId(), "Plano Pro não renovado no cartão", (c != null && c.getMotivo() != null ? c.getMotivo() + " " : "") +
                            "Pague o mês por Pix ou troque o cartão na tela do plano até " + data(ate + DIAS_TOLERANCIA * DIA) + ".", "plano-recusa:" + a.getId() + ":" + agora / DIA);
            } else if (d == Decisao.AVISAR_PIX) {
                a.avisou(agora); assinaturas.save(a);
                avisar(a.getUsuarioId(), "Renove o Plano Pro", "Seu plano vale até " + data(ate) + ". Pague o Pix de " + PagamentoService.reais(a.getValor()) + " na tela do plano para continuar com os benefícios.", "plano-aviso:" + a.getId() + ":" + ate);
            } else if (d == Decisao.ENCERRAR) {
                a.cancelar(agora); assinaturas.save(a);
                auditar(a.getUsuarioId(), "Plano Pro encerrado por falta de pagamento");
                avisar(a.getUsuarioId(), "Plano Pro encerrado", "O mês não foi pago. Assine de novo quando quiser, na tela do plano.", "plano-fim:" + a.getId() + ":" + ate);
            }
        }
        /* Acabou o plano: ficam as 10 primeiras categorias. */
        if (ate <= agora && servicos != null) {
            Usuario u = usuarios.findById(a.getUsuarioId()).orElse(null);
            if (u != null) {
                List<String> cats = servicos.de(u);
                if (cats.size() > CATEGORIAS) {
                    servicos.salvar(u, new ArrayList<>(cats.subList(0, CATEGORIAS)));
                    avisar(u.getId(), "Categorias ajustadas", "Sem o Plano Pro, ficam as " + CATEGORIAS + " primeiras: " + String.join(", ", cats.subList(0, CATEGORIAS).stream().map(PedidoContratadoController::nomeServico).toList()) + ".", "plano-categorias:" + a.getId() + ":" + ate);
                }
            }
        }
    }

    enum Decisao { NADA, COBRAR_CARTAO, AVISAR_PIX, ENCERRAR }
    /* Regra da renovação, sem banco (testável). */
    static Decisao decidir(String forma, boolean temCartao, long pagoAte, Long ultimaTentativa, Long avisoEnviado, long agora) {
        if (agora >= pagoAte + DIAS_TOLERANCIA * DIA) return Decisao.ENCERRAR;
        if (Assinatura.CARTAO.equals(forma) && temCartao) {
            boolean naJanela = agora >= pagoAte - DIA;
            boolean podeTentar = ultimaTentativa == null || agora - ultimaTentativa >= 20 * 3_600_000L;
            return naJanela && podeTentar ? Decisao.COBRAR_CARTAO : Decisao.NADA;
        }
        /* Pix (ou cartão sem cartão salvo): um aviso, 3 dias antes. */
        return agora >= pagoAte - DIAS_AVISO_PIX * DIA && avisoEnviado == null ? Decisao.AVISAR_PIX : Decisao.NADA;
    }

    /* ---------------- ajudas ---------------- */

    static long proximoMes(long base) { return Instant.ofEpochMilli(base).atZone(FUSO).plusMonths(1).toInstant().toEpochMilli(); }
    static String data(long ms) { return Instant.ofEpochMilli(ms).atZone(FUSO).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    private String validarForma(String forma, String metodo, String cartaoId, Long usuarioId) {
        String f = forma == null ? "" : forma.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Assinatura.CARTAO.equals(f) && !Assinatura.PIX.equals(f)) throw erro(HttpStatus.BAD_REQUEST, "Escolha cartão ou Pix.");
        if (Assinatura.PIX.equals(f) && !"pix".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Com Pix, o pagamento é por Pix.");
        if (Assinatura.CARTAO.equals(f)) {
            if ("cartao".equals(metodo)) {
                if (cartaoId == null || cartoes.findByIdAndUsuarioId(cartaoId, usuarioId).isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Escolha um dos seus cartões.");
            } else if ("cartao_novo".equals(metodo)) {
                if (!pagamentos.asaasAtivo()) throw erro(HttpStatus.BAD_REQUEST, "Cadastre um cartão primeiro.");
            } else throw erro(HttpStatus.BAD_REQUEST, "Escolha um cartão.");
        }
        return f;
    }
    private Usuario profissional(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Conta não encontrada."));
        if (!"profissional".equalsIgnoreCase(u.getTipo())) throw erro(HttpStatus.FORBIDDEN, "O Plano Pro é para quem atende.");
        return u;
    }
    private void avisar(Long usuarioId, String titulo, String texto, String chave) {
        if (notificacoes == null) return;
        try { notificacoes.criar(usuarioId, null, "conta", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
    private void auditar(Long usuarioId, String texto) { if (moderacao != null) moderacao.registrar(usuarioId, "pagamento", texto, usuarioId, null); }
    private static ResponseStatusException erro(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
}
