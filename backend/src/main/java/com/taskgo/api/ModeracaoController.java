package com.taskgo.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Situação da própria conta, recurso, e as ações do administrador sobre recursos e banimentos. */
@RestController
public class ModeracaoController {
    private final ModeracaoService moderacao;
    private final RecursoRepository recursos;
    private final UsuarioRepository usuarios;
    private final NotificacaoService notificacoes;
    private final EventosService eventos;

    public ModeracaoController(ModeracaoService moderacao, RecursoRepository recursos, UsuarioRepository usuarios,
                               NotificacaoService notificacoes, EventosService eventos) {
        this.moderacao = moderacao; this.recursos = recursos; this.usuarios = usuarios; this.notificacoes = notificacoes; this.eventos = eventos;
    }

    /* Histórico com data, para mostrar o motivo real de cada aviso na situação da conta. Opcional nos testes. */
    private HistoricoContaRepository historico;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setHistorico(HistoricoContaRepository historico) { this.historico = historico; }
    /* Só o que a pessoa fez e o que a moderação decidiu sobre ela: não mostra "acesso" da equipe nem eventos de pedido. */
    private static final java.util.Set<String> TIPOS_MODERACAO = java.util.Set.of("aviso", "suspensao", "banimento", "desbanimento", "recurso");

    @GetMapping("/api/moderacao/eu")
    @Transactional(readOnly = true)
    public Map<String, Object> eu(Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ModeracaoService.Situacao s = moderacao.situacao(u);
        List<RecursoDto> meus = recursos.findByUsuarioIdOrderByCriadoEmDesc(p.id()).stream().map(r -> RecursoDto.from(r, null, List.of())).toList();
        List<Map<String, Object>> hist = historico == null ? List.of()
                : historico.findByUsuarioIdOrderByCriadoEmDesc(p.id()).stream()
                        .filter(h -> TIPOS_MODERACAO.contains(h.getTipo())).limit(50)
                        .<Map<String, Object>>map(h -> Map.of("tipo", h.getTipo(), "texto", h.getTexto() == null ? "" : h.getTexto(), "quando", h.getCriadoEm(),
                                "pedidoId", h.getPedidoId() == null ? "" : h.getPedidoId()))
                        .toList();
        return Map.of("avisos", s.avisos(), "suspensoAte", s.suspensoAte() == null ? 0L : s.suspensoAte(), "suspenso", s.suspenso(),
                "banido", s.banido(), "administrador", moderacao.administrador(p.email()), "recursos", meus, "historico", hist);
    }

    @PostMapping("/api/moderacao/recursos")
    @Transactional
    public RecursoDto recorrer(@RequestBody Map<String, String> body, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        String texto = body == null ? null : body.get("texto");
        /* Aviso contestado (pedido). Sem ele, o recurso não abre conversa de pedido nenhum. */
        String pedidoId = body == null ? null : body.get("pedidoId");
        if (pedidoId != null && pedidoId.isBlank()) pedidoId = null;
        if (texto == null || texto.trim().length() < 10 || texto.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explique o que aconteceu em 10 a 2000 caracteres.");
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (moderacao.situacao(u).avisos() == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua conta não tem avisos para contestar.");
        if (recursos.findByUsuarioIdOrderByCriadoEmDesc(p.id()).stream().anyMatch(r -> Recurso.EM_ANALISE.equals(r.getStatus())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já tem um recurso em análise.");
        /* O pedido tem que estar entre os avisos da própria conta (identidade vem da sessão). */
        if (pedidoId != null && (historico == null || !historico.existsByUsuarioIdAndTipoAndPedidoId(p.id(), "aviso", pedidoId)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esse aviso não está na sua conta.");
        Recurso salvo;
        /* Dois envios ao mesmo tempo: o banco (uk_recursos_um_em_analise, V34) deixa só um em análise. */
        try { salvo = recursos.saveAndFlush(new Recurso(java.util.UUID.randomUUID().toString(), p.id(), pedidoId, texto.trim(), System.currentTimeMillis())); }
        catch (org.springframework.dao.DataIntegrityViolationException duplicado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já tem um recurso em análise.");
        }
        moderacao.registrar(p.id(), "recurso", "Enviou um recurso de moderação", p.id(), null);
        return RecursoDto.from(salvo, null, List.of());
    }

    /* ---------- Administrador ---------- */
    @GetMapping("/api/admin/recursos")
    @Transactional(readOnly = true)
    public List<RecursoDto> recursosAdmin(Authentication auth) {
        admin(auth);
        /* Fila: só os em análise, do mais antigo para o mais novo (os decididos ficam na auditoria). */
        return recursos.findByStatusOrderByCriadoEmAsc(Recurso.EM_ANALISE, org.springframework.data.domain.PageRequest.of(0, 100)).stream()
                .map(r -> RecursoDto.from(r, usuarios.findById(r.getUsuarioId()).map(this::resumo).orElse(null), avisosDe(r.getUsuarioId()))).toList();
    }

    @PostMapping("/api/admin/recursos/{id}/{decisao}")
    @Transactional
    public RecursoDto decidir(@PathVariable String id, @PathVariable String decisao, @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        TaskgoPrincipal adm = admin(auth);
        if (!"aprovar".equals(decisao) && !"negar".equals(decisao)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Recurso r = recursos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Recurso.EM_ANALISE.equals(r.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este recurso já foi decidido.");
        boolean aprovado = "aprovar".equals(decisao);
        String resposta = body == null ? null : body.get("resposta");
        r.decidir(aprovado ? Recurso.APROVADO : Recurso.NEGADO, adm.email(), resposta, System.currentTimeMillis());
        if (aprovado) moderacao.perdoar(r.getUsuarioId(), adm.id());
        else moderacao.registrar(r.getUsuarioId(), "recurso", "Recurso negado na revisão por pessoa da equipe; decisão mantida", adm.id(), null);
        notificacoes.criar(r.getUsuarioId(), null, "moderacao", aprovado ? "Recurso aprovado" : "Recurso negado",
                aprovado ? "Um aviso foi retirado da sua conta." : "A decisão foi mantida." + (resposta == null || resposta.isBlank() ? "" : " " + resposta), "recurso:" + r.getId());
        eventos.enviar(r.getUsuarioId(), "moderacao", null);
        return RecursoDto.from(r, usuarios.findById(r.getUsuarioId()).map(this::resumo).orElse(null), List.of());
    }

    @PostMapping("/api/admin/usuarios/{id}/{acao}")
    @Transactional
    public Map<String, Object> banir(@PathVariable Long id, @PathVariable String acao, Authentication auth) {
        TaskgoPrincipal adm = admin(auth);
        if (!"banir".equals(acao) && !"desbanir".equals(acao)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        moderacao.banir(id, "banir".equals(acao), adm.id());
        eventos.enviar(id, "moderacao", null);
        return usuarios.findById(id).map(this::resumo).orElseThrow();
    }

    private Map<String, Object> resumo(Usuario u) {
        ModeracaoService.Situacao s = moderacao.situacao(u);
        return Map.of("id", u.getId(), "nome", u.getNome() == null ? "" : u.getNome(), "email", u.getEmail(), "tipo", u.getTipo() == null ? "" : u.getTipo(),
                "avisos", s.avisos(), "suspenso", s.suspenso(), "banido", s.banido());
    }
    /* Avisos da conta, com o pedido em que a mensagem foi barrada: a equipe abre a conversa desse pedido. */
    private List<Map<String, Object>> avisosDe(Long usuarioId) {
        if (historico == null) return List.of();
        return historico.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream().filter(h -> "aviso".equals(h.getTipo())).limit(10)
                .<Map<String, Object>>map(h -> Map.of("texto", h.getTexto() == null ? "" : h.getTexto(), "quando", h.getCriadoEm(), "pedidoId", h.getPedidoId() == null ? "" : h.getPedidoId())).toList();
    }
    private TaskgoPrincipal admin(Authentication auth) {
        return PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.MODERACAO);
    }
    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }

    public record RecursoDto(String id, String texto, String status, long criadoEm, Long decididoEm, String resposta, Map<String, Object> usuario, List<Map<String, Object>> avisos, String pedidoId) {
        static RecursoDto from(Recurso r, Map<String, Object> usuario, List<Map<String, Object>> avisos) {
            return new RecursoDto(r.getId(), r.getTexto(), r.getStatus(), r.getCriadoEm(), r.getDecididoEm(), r.getResposta(), usuario, avisos, r.getPedidoId());
        }
    }
}
