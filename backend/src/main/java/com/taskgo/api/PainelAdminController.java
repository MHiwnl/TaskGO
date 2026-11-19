package com.taskgo.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static com.taskgo.api.AdminController.mapa;

/* Painel da equipe pensado para milhões de contas: mostra o que precisa de ação e responde a buscas; não lista tudo.
   Toda consulta é contagem por intervalo, busca exata com índice ou página pequena (fila de até 50, cursor na auditoria). */
@RestController
@RequestMapping("/api/admin/painel")
public class PainelAdminController {
    static final long HORA = 3_600_000L, DIA = 24 * HORA;
    private final ModeracaoService moderacao;
    private final UsuarioRepository usuarios;
    private final PedidoContratadoRepository pedidos;
    private final DenunciaRepository denuncias;
    private final RecursoRepository recursos;
    private final VerificacaoRepository verificacoes;
    private final ChamadoRepository chamados;
    private final HistoricoContaRepository historico;
    private final MetricasService metricas;
    private final ExportacaoService exportar;
    private final ExportacaoRepository exportacoes;

    public PainelAdminController(ModeracaoService moderacao, UsuarioRepository usuarios, PedidoContratadoRepository pedidos, DenunciaRepository denuncias,
                                 RecursoRepository recursos, VerificacaoRepository verificacoes, ChamadoRepository chamados, HistoricoContaRepository historico,
                                 MetricasService metricas, ExportacaoService exportar, ExportacaoRepository exportacoes) {
        this.moderacao = moderacao; this.usuarios = usuarios; this.pedidos = pedidos; this.denuncias = denuncias; this.recursos = recursos;
        this.verificacoes = verificacoes; this.chamados = chamados; this.historico = historico; this.metricas = metricas;
        this.exportar = exportar; this.exportacoes = exportacoes;
    }

    /* Papel de quem está logado e as áreas que ele vê (o site monta as abas com isto). */
    @GetMapping("/eu")
    public Map<String, Object> eu(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String papel = PapelEquipe.papelDe(p, moderacao);
        return mapa("papel", papel, "areas", PapelEquipe.areas(papel).stream().map(Enum::name).toList());
    }

    /* ---------------- Hoje ---------------- */
    @GetMapping("/hoje")
    @Transactional(readOnly = true)
    public Map<String, Object> hoje(Authentication auth) {
        TaskgoPrincipal p = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.HOJE);
        Set<PapelEquipe.Area> areas = PapelEquipe.areas(PapelEquipe.papelDe(p, moderacao));
        long agora = System.currentTimeMillis();

        Map<String, Object> pend = new LinkedHashMap<>();
        if (areas.contains(PapelEquipe.Area.MODERACAO)) {
            pend.put("denuncias", denuncias.countByStatus(Denuncia.EM_ANALISE));
            pend.put("recursos", recursos.countByStatus(Recurso.EM_ANALISE));
            pend.put("verificacoes", verificacoes.countByStatus(Verificacao.PENDENTE));
        }
        if (areas.contains(PapelEquipe.Area.PAGAMENTOS)) pend.put("pagamentos", problemasPagamento(Integer.MAX_VALUE).size());
        pend.put("emergencias", chamados.countByStatusIn(List.of(Chamado.ABERTO, Chamado.EM_ATENDIMENTO)));

        List<Map<String, Object>> numeros = new ArrayList<>();
        if (areas.contains(PapelEquipe.Area.NUMEROS) || areas.contains(PapelEquipe.Area.ATENCAO)) {
            MetricasService.Numeros h = metricas.calcular(agora - DIA, agora), o = metricas.calcular(agora - 2 * DIA, agora - DIA), s = metricas.calcular(agora - 8 * DIA, agora - 7 * DIA);
            numeros.add(numero("pedidos", "Pedidos", h.pedidos(), o.pedidos(), s.pedidos()));
            numeros.add(numero("concluidos", "Concluídos", h.concluidos(), o.concluidos(), s.concluidos()));
            numeros.add(numero("valor", "Valor concluído", h.valor(), o.valor(), s.valor()));
            numeros.add(numero("contas", "Contas novas", h.contas(), o.contas(), s.contas()));
            numeros.add(numero("cancelados", "Cancelamentos", h.cancelados(), o.cancelados(), s.cancelados()));
            numeros.add(numero("aceite", "Minutos até o aceite", medianaAceite(agora - DIA, agora), medianaAceite(agora - 2 * DIA, agora - DIA), medianaAceite(agora - 8 * DIA, agora - 7 * DIA)));
        }
        return mapa("papel", PapelEquipe.papelDe(p, moderacao), "pendencias", pend, "numeros", numeros, "alertas", alertas(areas, agora), "geradoEm", agora);
    }
    private static Map<String, Object> numero(String chave, String rotulo, Object agora, Object ontem, Object semana) {
        return mapa("chave", chave, "rotulo", rotulo, "valor", agora, "ontem", ontem, "semanaPassada", semana);
    }
    private Long medianaAceite(long a, long b) {
        long n = pedidos.aceitosEntre(a, b);
        if (n == 0) return null;
        return pedidos.esperasEntre(a, b, PageRequest.of((int) (n / 2), 1)).stream().findFirst().map(ms -> ms / 60_000).orElse(null);
    }
    private List<Map<String, Object>> alertas(Set<PapelEquipe.Area> areas, long agora) {
        List<Map<String, Object>> r = new ArrayList<>();
        long emerg = chamados.countByStatusIn(List.of(Chamado.ABERTO));
        if (emerg > 0) r.add(mapa("nivel", "alto", "texto", emerg + " emergência(s) sem ninguém atendendo.", "area", "HOJE"));
        if (areas.contains(PapelEquipe.Area.NUMEROS)) {
            List<Map<String, Object>> semana = metricas.serie(8);
            double mediaCanc = semana.stream().limit(7).mapToLong(d -> (Long) d.get("cancelados")).average().orElse(0);
            double mediaPed = semana.stream().limit(7).mapToLong(d -> (Long) d.get("pedidos")).average().orElse(0);
            MetricasService.Numeros h = metricas.calcular(agora - DIA, agora);
            if (h.cancelados() >= 10 && h.cancelados() > 2 * mediaCanc)
                r.add(mapa("nivel", "medio", "texto", "Cancelamentos nas últimas 24 h (" + h.cancelados() + ") passam do dobro da média dos últimos 7 dias (" + Math.round(mediaCanc) + ").", "area", "NUMEROS"));
            if (mediaPed >= 20 && h.pedidos() < mediaPed / 2)
                r.add(mapa("nivel", "medio", "texto", "Pedidos nas últimas 24 h (" + h.pedidos() + ") abaixo da metade da média dos últimos 7 dias (" + Math.round(mediaPed) + ").", "area", "NUMEROS"));
        }
        if (areas.contains(PapelEquipe.Area.MODERACAO))
            for (Object[] l : denuncias.muitasDesde(agora - 7 * DIA, 3).stream().limit(5).toList()) {
                Long conta = ((Number) l[0]).longValue();
                String nome = usuarios.findById(conta).map(Usuario::getNome).orElse("Conta " + conta);
                r.add(mapa("nivel", "medio", "texto", nome + " recebeu " + l[1] + " denúncias em 7 dias.", "area", "BUSCAR", "conta", conta));
            }
        if (areas.contains(PapelEquipe.Area.ATENCAO)) {
            long parados = pedidos.contarParadosDesde("aguardando_pagamento", agora - DIA);
            if (parados > 0) r.add(mapa("nivel", "baixo", "texto", parados + " pedido(s) esperando pagamento há mais de 24 h.", "area", "ATENCAO"));
        }
        return r;
    }

    /* ---------------- Buscar ---------------- */
    @GetMapping("/buscar")
    @Transactional(readOnly = true)
    public Map<String, Object> buscar(@RequestParam String q, Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.BUSCAR);
        String t = q == null ? "" : q.trim();
        if (t.length() < 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Digite pelo menos 3 caracteres.");
        Set<Usuario> contas = new LinkedHashSet<>();
        List<Pedido> peds = new ArrayList<>();
        String digitos = t.replaceAll("\\D", "");
        String como;
        if (t.contains("@")) { como = "e-mail"; usuarios.findByEmailIgnoreCase(t).ifPresent(contas::add); }
        else if (t.startsWith("o_")) { como = "número do pedido"; pedidos.findById(t).ifPresent(peds::add); }
        else if (digitos.length() == 11 && digitos.length() >= t.replaceAll("[\\s.()\\-]", "").length()) {
            como = "CPF ou telefone";
            contas.addAll(usuarios.findByCpfIn(List.of(digitos, digitos.replaceAll("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4"))));
            contas.addAll(usuarios.findByTelefoneIn(formasTelefone(digitos)));
        } else if (digitos.length() == 10 && digitos.length() >= t.replaceAll("[\\s.()\\-]", "").length()) {
            como = "telefone"; contas.addAll(usuarios.findByTelefoneIn(formasTelefone(digitos)));
        } else if (t.matches("\\d+")) {
            como = "id da conta"; usuarios.findById(Long.valueOf(t)).ifPresent(contas::add);
        } else {
            como = "nome (começo)";
            contas.addAll(usuarios.buscarPorNome(t.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "\\_") + "%", PageRequest.of(0, 20)));
        }
        return mapa("como", como,
                "contas", contas.stream().limit(20).map(u -> {
                    ModeracaoService.Situacao s = moderacao.situacao(u);
                    return mapa("id", u.getId(), "nome", u.getNome(), "email", u.getEmail(), "tipo", u.getTipo(), "cidade", u.getCidade(), "criadoEm", u.getCriadoEm(),
                            "situacao", s.banido() ? "banida" : s.suspenso() ? "suspensa" : s.avisos() > 0 ? "com aviso" : "ativa", "verificado", VerificacaoController.verificado(u));
                }).toList(),
                "pedidos", peds.stream().map(this::linhaPedido).toList());
    }
    private static List<String> formasTelefone(String d) {
        List<String> r = new ArrayList<>(List.of(d));
        if (d.length() == 11) r.add("(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7));
        if (d.length() == 10) r.add("(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6));
        return r;
    }
    private Map<String, Object> linhaPedido(Pedido p) {
        return mapa("id", p.getId(), "servico", p.getService(), "status", AdminController.status(p), "valor", p.getPrice(), "criadoEm", p.getCreated(),
                "aceitoEm", p.getAcceptedAt(), "bairro", p.getExtra().get("bairro"), "pagamento", p.getPaid(), "urgente", AdminController.urgente(p));
    }

    /* ---------------- Pedidos que precisam de atenção ---------------- */
    @GetMapping("/atencao")
    @Transactional(readOnly = true)
    public Map<String, Object> atencao(Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.ATENCAO);
        long agora = System.currentTimeMillis();
        PageRequest top = PageRequest.of(0, 50);
        List<Map<String, Object>> problemas = problemasPagamento(50);
        return mapa(
                "pagamentoParado", grupo("Esperando pagamento há mais de 24 h", pedidos.contarParadosDesde("aguardando_pagamento", agora - DIA),
                        pedidos.paradosDesde("aguardando_pagamento", agora - DIA, top)),
                "semAceite", grupo("Sem profissional há mais de 2 h", pedidos.contarParadosDesde("aguardando", agora - 2 * HORA),
                        pedidos.paradosDesde("aguardando", agora - 2 * HORA, top)),
                "aCaminho", grupo("A caminho há mais de 3 h", pedidos.contarACaminhoDesde(agora - 3 * HORA), pedidos.aCaminhoDesde(agora - 3 * HORA, top)),
                "problemas", mapa("titulo", "Problema relatado depois da conclusão (pagamento retido)", "total", problemas.size(), "itens", problemas));
    }
    private Map<String, Object> grupo(String titulo, long total, List<Pedido> itens) {
        return mapa("titulo", titulo, "total", total, "itens", itens.stream().map(this::linhaPedido).toList());
    }
    /* Concluídos com pagamento retido e problema aberto pelo cliente (fila do financeiro). */
    private List<Map<String, Object>> problemasPagamento(int limite) {
        return pedidos.findByStatusAndPaid("concluido", "retido").stream()
                .filter(p -> "problema".equals(p.getExtra().get("liberacao")))
                .sorted(java.util.Comparator.comparingLong(p -> p.getExtra().get("problemaEm") instanceof Number n ? n.longValue() : 0L))
                .limit(limite)
                .map(p -> { Map<String, Object> m = linhaPedido(p); m.put("problema", p.getExtra().get("problema")); m.put("problemaEm", p.getExtra().get("problemaEm")); return m; })
                .toList();
    }
    @GetMapping("/pagamentos")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> filaPagamentos(Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS);
        return problemasPagamento(100);
    }

    /* ---------------- Auditoria ---------------- */
    @GetMapping("/auditoria")
    @Transactional(readOnly = true)
    public Map<String, Object> auditoria(@RequestParam(required = false) Long conta, @RequestParam(defaultValue = "") String pedido,
                                         @RequestParam(defaultValue = "") String tipo, @RequestParam(defaultValue = "") String de,
                                         @RequestParam(defaultValue = "") String ate, @RequestParam(defaultValue = "") String antes, Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.AUDITORIA);
        long ini, fim;
        try {
            ini = de.isBlank() ? 0 : MetricasService.inicio(LocalDate.parse(de));
            fim = ate.isBlank() ? Long.MAX_VALUE : MetricasService.inicio(LocalDate.parse(ate).plusDays(1));
        } catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data inválida. Use aaaa-mm-dd."); }
        /* Cursor "quando:id" do último item da página anterior. */
        long cursor = Long.MAX_VALUE; String cursorId = "￿";
        if (!antes.isBlank()) {
            int i = antes.indexOf(':');
            try { cursor = Long.parseLong(antes.substring(0, i)); cursorId = antes.substring(i + 1); }
            catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor inválido."); }
        }
        List<HistoricoConta> lista = historico.auditoria(conta == null ? -1 : conta, pedido.trim(), tipo.trim(), ini, fim, cursor, cursorId, PageRequest.of(0, 51));
        boolean mais = lista.size() > 50;
        List<HistoricoConta> pagina = mais ? lista.subList(0, 50) : lista;
        /* Nomes só das contas que aparecem nesta página. */
        Set<Long> ids = new LinkedHashSet<>();
        pagina.forEach(h -> { ids.add(h.getUsuarioId()); if (h.getAutorId() != null) ids.add(h.getAutorId()); });
        Map<Long, Usuario> nomes = new java.util.HashMap<>();
        usuarios.findAllById(ids).forEach(u -> nomes.put(u.getId(), u));
        HistoricoConta ultimo = pagina.isEmpty() ? null : pagina.get(pagina.size() - 1);
        return mapa("itens", pagina.stream().map(h -> mapa("quando", h.getCriadoEm(), "tipo", h.getTipo(), "texto", h.getTexto(), "pedidoId", h.getPedidoId(),
                        "contaId", h.getUsuarioId(), "conta", nome(nomes.get(h.getUsuarioId())), "autorId", h.getAutorId(),
                        "autor", h.getAutorId() == null ? null : nome(nomes.get(h.getAutorId())), "autorTipo", h.getAutorId() == null || nomes.get(h.getAutorId()) == null ? null : nomes.get(h.getAutorId()).getTipo())).toList(),
                "proximo", mais && ultimo != null ? ultimo.getCriadoEm() + ":" + ultimo.getId() : null);
    }
    private static String nome(Usuario u) { return u == null ? "Conta excluída" : u.getNome() == null || u.getNome().isBlank() ? "Sem nome" : u.getNome(); }

    /* ---------------- Números ---------------- */
    @GetMapping("/numeros")
    @Transactional(readOnly = true)
    public Map<String, Object> numeros(@RequestParam(defaultValue = "30") int dias, Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.NUMEROS);
        int periodo = Math.max(7, Math.min(dias, 365));
        List<Map<String, Object>> serie = metricas.serie(periodo);
        long pedidosP = 0, concl = 0, canc = 0, contas = 0; BigDecimal valor = BigDecimal.ZERO;
        for (Map<String, Object> d : serie) { pedidosP += (Long) d.get("pedidos"); concl += (Long) d.get("concluidos"); canc += (Long) d.get("cancelados"); contas += (Long) d.get("contasNovas"); valor = valor.add((BigDecimal) d.get("valor")); }
        long agora = System.currentTimeMillis(), inicio = MetricasService.inicio(LocalDate.now(MetricasService.FUSO).minusDays(periodo - 1L));
        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (Object[] l : usuarios.contarPorTipo()) porTipo.put(String.valueOf(l[0]), ((Number) l[1]).longValue());
        List<Map<String, Object>> categorias = pedidos.categoriasEntre(inicio, agora + 1).stream()
                .map(l -> mapa("servico", l[0], "pedidos", ((Number) l[1]).longValue()))
                .sorted((x, y) -> Long.compare((Long) y.get("pedidos"), (Long) x.get("pedidos"))).toList();
        return mapa("dias", periodo, "serie", serie,
                "periodo", mapa("pedidos", pedidosP, "concluidos", concl, "cancelados", canc, "contasNovas", contas, "valor", valor,
                        "taxaConclusao", concl + canc == 0 ? null : AdminController.arred(100.0 * concl / (concl + canc)),
                        "ticketMedio", concl == 0 ? null : valor.divide(BigDecimal.valueOf(concl), 2, java.math.RoundingMode.HALF_UP)),
                "contas", mapa("clientes", porTipo.getOrDefault("cliente", 0L), "profissionais", porTipo.getOrDefault("profissional", 0L)),
                "categorias", categorias);
    }

    /* ---------------- Exportar ---------------- */
    public record PedidoExportacao(String tipo, String de, String ate) {}
    @PostMapping("/exportacoes")
    public Map<String, Object> pedirExportacao(@RequestBody PedidoExportacao body, Authentication auth) {
        TaskgoPrincipal p = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.EXPORTAR);
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o tipo e o período.");
        LocalDate de, ate;
        try { de = LocalDate.parse(String.valueOf(body.de())); ate = LocalDate.parse(String.valueOf(body.ate())); }
        catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o período (aaaa-mm-dd)."); }
        Exportacao e = exportar.pedir(p.id(), String.valueOf(body.tipo()), de, ate);
        moderacao.registrar(p.id(), "equipe", "Pediu a planilha de " + e.getTipo() + " de " + de + " a " + ate, p.id(), null);
        return mapa("id", e.getId(), "status", e.getStatus());
    }
    @GetMapping("/exportacoes")
    @Transactional(readOnly = true)
    public List<ExportacaoRepository.Resumo> minhasExportacoes(Authentication auth) {
        TaskgoPrincipal p = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.EXPORTAR);
        return exportacoes.doAutor(p.id(), PageRequest.of(0, 20));
    }
    @GetMapping("/exportacoes/{id}/arquivo")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> baixar(@PathVariable String id, Authentication auth) {
        TaskgoPrincipal p = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.EXPORTAR);
        Exportacao e = exportacoes.findById(id).filter(x -> x.getAutorId().equals(p.id())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Exportacao.PRONTA.equals(e.getStatus()) || e.getArquivo() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "A planilha ainda não está pronta.");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"taskgo-" + e.getTipo() + "-" + e.getDe() + "-a-" + e.getAte() + ".csv\"")
                .header("X-Content-Type-Options", "nosniff").body(e.getArquivo());
    }
}
