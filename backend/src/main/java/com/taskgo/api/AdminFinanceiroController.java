package com.taskgo.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Financeiro do administrador: o que a TaskGO recebeu de taxas, o saldo real da conta no Asaas
 * e a transferência desse dinheiro para a chave Pix que o administrador escolher.
 * O valor dos serviços não passa por aqui: o cliente paga direto a quem atende.
 */
@RestController
public class AdminFinanceiroController {
    private static final ZoneId FUSO = ZoneId.of("America/Recife");
    private final CobrancaRepository cobrancas;
    private final TransacaoFinanceiraRepository transacoes;
    private final UsuarioRepository usuarios;
    private final PagamentoService pagamentos;
    private final AsaasCliente asaas;
    private final ModeracaoService moderacao;
    private AssinaturaRepository assinaturas;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setAssinaturas(AssinaturaRepository a) { this.assinaturas = a; }
    private CarteiraRepository carteiras;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setCarteiras(CarteiraRepository c) { this.carteiras = c; }
    private SolicitacaoSaqueRepository saques;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setSaques(SolicitacaoSaqueRepository s) { this.saques = s; }
    /* O que pode sair da conta: o saldo do Asaas menos o que é dos usuários. */
    private BigDecimal livre(BigDecimal saldoAsaas) { return saldoAsaas.subtract(carteiras == null ? BigDecimal.ZERO : carteiras.totalDosUsuarios()).max(BigDecimal.ZERO); }

    public AdminFinanceiroController(CobrancaRepository cobrancas, TransacaoFinanceiraRepository transacoes, UsuarioRepository usuarios,
                                     PagamentoService pagamentos, AsaasCliente asaas, ModeracaoService moderacao) {
        this.cobrancas = cobrancas; this.transacoes = transacoes; this.usuarios = usuarios; this.pagamentos = pagamentos; this.asaas = asaas; this.moderacao = moderacao;
    }

    @GetMapping("/api/admin/financeiro")
    public Map<String, Object> resumo(Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.FINANCEIRO);
        LocalDate hoje = LocalDate.now(FUSO);
        long inicio30 = hoje.minusDays(29).atStartOfDay(FUSO).toInstant().toEpochMilli();
        long inicioMes = hoje.withDayOfMonth(1).atStartOfDay(FUSO).toInstant().toEpochMilli();
        long inicioHoje = hoje.atStartOfDay(FUSO).toInstant().toEpochMilli();
        /* A busca vai 30 dias além: um Pix criado no fim do mês e pago no começo do outro conta no mês do pagamento (os totais filtram por pagaEm). */
        long desde = Math.min(inicio30, inicioMes) - 30L * 86_400_000L;
        /* Receita: taxas pagas (cobranças de pendência confirmadas, à mão ou no cartão autorizado) e mensalidades do Plano Pro. */
        List<Cobranca> pagas = new ArrayList<>(cobrancas.findByTipoAndStatusAndCriadaEmGreaterThanEqualOrderByCriadaEmDesc("pendencia", Cobranca.PAGA, desde));
        pagas.addAll(cobrancas.findByTipoAndStatusAndCriadaEmGreaterThanEqualOrderByCriadaEmDesc("plano", Cobranca.PAGA, desde));
        pagas.sort(java.util.Comparator.comparingLong((Cobranca c) -> c.getPagaEm() != null ? c.getPagaEm() : c.getCriadaEm()).reversed());
        TreeMap<LocalDate, BigDecimal> porDia = new TreeMap<>();
        for (int i = 29; i >= 0; i--) porDia.put(hoje.minusDays(i), BigDecimal.ZERO);
        BigDecimal mes = BigDecimal.ZERO, dia = BigDecimal.ZERO, trinta = BigDecimal.ZERO, planosMes = BigDecimal.ZERO;
        for (Cobranca c : pagas) {
            long quando = c.getPagaEm() != null ? c.getPagaEm() : c.getCriadaEm();
            BigDecimal v = c.getValor();
            LocalDate d = Instant.ofEpochMilli(quando).atZone(FUSO).toLocalDate();
            if (porDia.containsKey(d)) { porDia.merge(d, v, BigDecimal::add); trinta = trinta.add(v); }
            if (quando >= inicioMes) { mes = mes.add(v); if ("plano".equals(c.getTipo())) planosMes = planosMes.add(v); }
            if (quando >= inicioHoje) dia = dia.add(v);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modo", pagamentos.modo()); r.put("teste", pagamentos.teste()); r.put("asaas", pagamentos.asaasAtivo());
        r.put("hoje", dia); r.put("mes", mes); r.put("trintaDias", trinta);
        r.put("planosMes", planosMes);
        r.put("assinantesPro", assinaturas == null ? 0 : assinaturas.countByPagoAteGreaterThan(System.currentTimeMillis()));
        List<Map<String, Object>> serie = new ArrayList<>();
        porDia.forEach((d, v) -> serie.add(Map.of("dia", d.toString(), "valor", v)));
        r.put("porDia", serie);
        List<Map<String, Object>> ultimas = new ArrayList<>();
        for (Cobranca c : pagas.stream().limit(40).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            boolean plano = "plano".equals(c.getTipo());
            m.put("id", c.getId()); m.put("valor", c.getValor()); m.put("metodo", c.getMetodo()); m.put("pedidoId", plano ? null : c.getPedidoId());
            m.put("origem", plano ? "Plano Pro" : "Taxa");
            m.put("em", c.getPagaEm() != null ? c.getPagaEm() : c.getCriadaEm());
            m.put("profissional", c.getClienteId() == null ? "Conta excluída" : usuarios.findById(c.getClienteId()).map(Usuario::getNome).orElse("Conta excluída"));
            ultimas.add(m);
        }
        r.put("ultimas", ultimas);
        /* Saldo real da conta no Asaas (o que pode ser transferido agora). */
        if (pagamentos.asaasAtivo() && asaas.configurado()) {
            try { BigDecimal s = new BigDecimal(asaas.saldo().path("balance").asText("0")); r.put("saldoAsaas", s); r.put("saldo", livre(s)); }
            catch (RuntimeException e) { r.put("saldoErro", "O Asaas não respondeu agora. Tente de novo em instantes."); }
        }
        List<Map<String, Object>> envios = new ArrayList<>();
        for (TransacaoFinanceira t : transacoes.findTop30ByTipoOrderByCriadaEmDesc("TRANSFERENCIA_TASKGO")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId()); m.put("valor", t.getValor()); m.put("status", t.getStatus()); m.put("em", t.getCriadaEm()); m.put("destino", t.getReferencia());
            envios.add(m);
        }
        r.put("transferencias", envios);
        /* Resgates sem resposta do Asaas: a equipe confere no painel do Asaas. */
        List<Map<String, Object>> verificar = new ArrayList<>();
        if (saques != null) for (SolicitacaoSaque s : saques.findByStatus("VERIFICAR"))
            verificar.add(Map.of("id", s.getId(), "valor", s.getValor(), "em", s.getCriadaEm(), "profissional", usuarios.findById(s.getUsuarioId()).map(Usuario::getNome).orElse("Conta excluída")));
        r.put("resgatesVerificar", verificar);
        /* Devoluções que só a equipe resolve (cobrança do Asaas sem identificador): devolver no painel do Asaas e marcar aqui. */
        List<Map<String, Object>> devolver = new ArrayList<>();
        for (TransacaoFinanceira t : transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")) {
            Map<String, Object> m = new LinkedHashMap<>();
            /* Cobrança que voltou a ter como devolver sozinha: aparece, sem o botão, para a equipe ver se a devolução automática não sai. */
            m.put("automatica", !pagamentos.conferenciaManual(t.getReferencia()));
            m.put("id", t.getId()); m.put("valor", pagamentos.valorDaConferencia(t)); m.put("em", t.getCriadaEm());
            m.put("cliente", t.getUsuarioId() == null ? "Conta excluída" : usuarios.findById(t.getUsuarioId()).map(Usuario::getNome).orElse("Conta excluída"));
            m.put("pedidoId", cobrancas.findById(t.getReferencia()).map(Cobranca::getPedidoId).orElse(null));
            devolver.add(m);
        }
        r.put("devolucoesConferir", devolver);
        return r;
    }

    public record Transferencia(BigDecimal valor, String tipo, String chave, String descricao) {}
    public record Resolucao(Boolean saiu) {}

    /** Resgate sem resposta do Asaas: depois de conferir no painel do Asaas, a equipe conclui ou devolve. */
    @PostMapping("/api/admin/financeiro/resgates/{id}/resolver")
    public Map<String, Object> resolverResgate(@org.springframework.web.bind.annotation.PathVariable String id, @RequestBody Resolucao body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.FINANCEIRO);
        if (resgates == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Resgates indisponíveis.");
        if (body == null || body.saiu() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diga se a transferência saiu ou não.");
        SolicitacaoSaque s = resgates.resolverVerificar(id, body.saiu());
        moderacao.registrar(a.id(), "pagamento", (body.saiu() ? "Concluiu" : "Devolveu") + " o resgate de " + PagamentoService.reais(s.getValor()) + " em conferência", a.id(), null);
        return Map.of("status", s.getStatus());
    }
    /** Transferência da TaskGO sem resposta do Asaas: a equipe conferiu no painel e marca se saiu. */
    @PostMapping("/api/admin/financeiro/transferencias/{id}/resolver")
    public Map<String, Object> resolverTransferencia(@org.springframework.web.bind.annotation.PathVariable String id, @RequestBody Resolucao body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.FINANCEIRO);
        if (body == null || body.saiu() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diga se a transferência saiu ou não.");
        TransacaoFinanceira t = transacoes.findById(id).filter(x -> "TRANSFERENCIA_TASKGO".equals(x.getTipo()) && "VERIFICAR".equals(x.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transferência em conferência não encontrada."));
        t.situacao(body.saiu() ? "DONE" : "FALHOU"); transacoes.save(t);
        moderacao.registrar(a.id(), "pagamento", "Marcou a transferência de " + PagamentoService.reais(t.getValor()) + " como " + (body.saiu() ? "enviada" : "não enviada"), a.id(), null);
        return Map.of("status", t.getStatus());
    }
    /** Devolução em conferência: a equipe já devolveu pelo painel do Asaas e marca aqui. */
    @PostMapping("/api/admin/financeiro/devolucoes/{id}/devolvida")
    public Map<String, Object> devolucaoFeita(@org.springframework.web.bind.annotation.PathVariable String id, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.FINANCEIRO);
        BigDecimal valor = pagamentos.devolvidaNaConferencia(id, a.id());
        if (valor == null) return Map.of("status", PagamentoService.CONFERENCIA_AUTOMATICA, "aviso", "O Asaas voltou a aceitar esta cobrança e a devolução sai sozinha. Não devolva pelo painel do Asaas.");
        if (valor.signum() <= 0) return Map.of("status", "DONE", "valor", valor, "aviso", "Nada a registrar: esta cobrança já estava devolvida. Confira no Asaas se não houve devolução em dobro.");
        moderacao.registrar(a.id(), "pagamento", "Marcou como feita no Asaas a devolução de " + PagamentoService.reais(valor), a.id(), null);
        return Map.of("status", "DONE", "valor", valor);
    }
    private ResgateService resgates;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setResgates(ResgateService r) { this.resgates = r; }
    /* Uma transferência da TaskGO por vez: o saldo livre é lido de novo depois que a anterior saiu.
       shortcut: trava só desta instância da API; com mais de uma instância, trocar por trava no banco. */
    private static final Object TRANSFERINDO = new Object();

    /** Manda o dinheiro da conta da TaskGO no Asaas para a chave Pix que o administrador escolher. */
    @PostMapping("/api/admin/financeiro/transferir")
    public Map<String, Object> transferir(@RequestBody Transferencia body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.FINANCEIRO);
        if (!pagamentos.asaasAtivo() || !asaas.configurado()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A transferência só existe com o Asaas ligado.");
        if (body == null || body.valor() == null || body.valor().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o valor.");
        BigDecimal valor = body.valor().setScale(2, RoundingMode.HALF_UP);
        ResgateService.ChavePix k = ResgateService.normalizarChave(body.tipo(), body.chave());
        synchronized (TRANSFERINDO) { return transferirTravado(a, body, valor, k); }
    }
    private Map<String, Object> transferirTravado(TaskgoPrincipal a, Transferencia body, BigDecimal valor, ResgateService.ChavePix k) {
        if (!transacoes.findByTipoAndStatus("TRANSFERENCIA_TASKGO", "VERIFICAR").isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há uma transferência sem resposta do Asaas. Confira no painel do Asaas e marque se ela saiu antes de transferir de novo.");
        BigDecimal saldo;
        try { saldo = livre(new BigDecimal(asaas.saldo().path("balance").asText("0"))); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O Asaas não respondeu agora. Tente de novo em instantes."); }
        if (valor.compareTo(saldo) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Pode sair até " + PagamentoService.reais(saldo) + ": o resto do saldo do Asaas é dos usuários.");
        String descricao = body.descricao() == null || body.descricao().isBlank() ? "Transferência TaskGO" : body.descricao().trim().substring(0, Math.min(80, body.descricao().trim().length()));
        String id, status;
        try {
            var r = asaas.transferirPix(valor, k.chave(), k.tipo(), descricao);
            id = r.path("id").asText(""); status = r.path("status").asText("PENDING");
        } catch (AsaasCliente.Erro e) {
            if (e.status != 503) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            /* Sem resposta: a transferência pode ter saído. Registra para conferir, e não deixa repetir às cegas. */
            id = ""; status = "VERIFICAR";
        }
        String destino = k.tipo() + " " + mascarar(k.chave());
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), a.id(), "TRANSFERENCIA_TASKGO", valor, status, destino + (id.isBlank() ? "" : " · " + id), System.currentTimeMillis()));
        moderacao.registrar(a.id(), "pagamento", "Transferiu " + PagamentoService.reais(valor) + " da conta da TaskGO para " + destino, a.id(), null);
        if ("VERIFICAR".equals(status)) return Map.of("status", status, "valor", valor, "destino", destino, "aviso", "O Asaas não respondeu. Confira no painel do Asaas se a transferência saiu antes de tentar de novo.");
        return Map.of("status", status, "valor", valor, "destino", destino);
    }
    /* Na lista e no registro, a chave aparece só pela metade. */
    private static String mascarar(String chave) {
        if (chave == null || chave.length() <= 6) return "***";
        return chave.substring(0, 3) + "***" + chave.substring(chave.length() - 3);
    }
}
