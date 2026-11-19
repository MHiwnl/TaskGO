package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Denunciar quem esteve no mesmo pedido. O administrador decide; procedente pode virar aviso na conta denunciada. */
@RestController
public class DenunciaController {
    static final Map<String, String> MOTIVOS = Map.of("contato_fora", "Pediu contato ou pagamento por fora", "comportamento", "Comportamento ofensivo ou agressivo",
            "nao_compareceu", "Não apareceu ou sumiu", "servico", "Serviço malfeito ou diferente do combinado", "golpe", "Tentativa de golpe", "outro", "Outro motivo");
    private final DenunciaRepository denuncias;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final ModeracaoService moderacao;
    private final NotificacaoService notificacoes;
    private final EventosService eventos;

    public DenunciaController(DenunciaRepository denuncias, PedidoContratadoRepository pedidos, UsuarioRepository usuarios,
                              ModeracaoService moderacao, NotificacaoService notificacoes, EventosService eventos) {
        this.denuncias = denuncias; this.pedidos = pedidos; this.usuarios = usuarios; this.moderacao = moderacao; this.notificacoes = notificacoes; this.eventos = eventos;
    }

    public record NovaDenuncia(String pedidoId, String motivo, String texto) {}

    @PostMapping("/api/denuncias")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> denunciar(@RequestBody NovaDenuncia body, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        if (body == null || body.pedidoId() == null || !MOTIVOS.containsKey(body.motivo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha o motivo da denúncia.");
        String texto = body.texto() == null ? "" : body.texto().trim();
        if (texto.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conte o que aconteceu em até 2000 caracteres.");
        if ("outro".equals(body.motivo()) && texto.length() < 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conte o que aconteceu (pelo menos 10 caracteres).");
        Pedido pedido = pedidos.findById(body.pedidoId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        String eu = p.email().toLowerCase(Locale.ROOT);
        String outroEmail = eu.equalsIgnoreCase(String.valueOf(pedido.getClientEmail())) ? pedido.getProEmail()
                : eu.equalsIgnoreCase(String.valueOf(pedido.getProEmail())) ? pedido.getClientEmail() : null;
        if (outroEmail == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só dá para denunciar quem esteve no mesmo pedido que você.");
        Usuario outro = usuarios.findByEmailIgnoreCase(outroEmail).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "A outra conta não existe mais."));
        /* Uma denúncia por pessoa em cada pedido. O banco também garante (uk_denuncias_denunciante_pedido), mesmo com dois cliques ao mesmo tempo. */
        if (denuncias.existsByDenuncianteIdAndPedidoId(p.id(), pedido.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já denunciou este pedido. A equipe da TaskGO está analisando.");
        Denuncia d;
        try { d = denuncias.saveAndFlush(new Denuncia(p.id(), outro.getId(), pedido.getId(), body.motivo(), texto.isEmpty() ? null : texto, System.currentTimeMillis())); }
        catch (org.springframework.dao.DataIntegrityViolationException duplicada) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já denunciou este pedido. A equipe da TaskGO está analisando.");
        }
        moderacao.registrar(outro.getId(), "denuncia", "Denúncia: " + MOTIVOS.get(body.motivo()), p.id(), pedido.getId());
        return dto(d, false);
    }

    @GetMapping("/api/denuncias/minhas")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> minhas(Authentication auth) {
        return denuncias.findByDenuncianteIdOrderByCriadaEmDesc(principal(auth).id()).stream().map(d -> dto(d, false)).toList();
    }

    /* ---------- Administrador ---------- */
    @GetMapping("/api/admin/denuncias")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> todas(Authentication auth) {
        admin(auth);
        return denuncias.findByStatusOrderByCriadaEmAsc(Denuncia.EM_ANALISE, org.springframework.data.domain.PageRequest.of(0, 100)).stream().map(d -> dto(d, true)).toList();
    }

    public record Decisao(String resposta, Boolean aviso) {}

    @PostMapping("/api/admin/denuncias/{id}/{decisao}")
    @Transactional
    public Map<String, Object> decidir(@PathVariable String id, @PathVariable String decisao, @RequestBody(required = false) Decisao body, Authentication auth) {
        TaskgoPrincipal adm = admin(auth);
        if (!"procedente".equals(decisao) && !"improcedente".equals(decisao)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Denuncia d = denuncias.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Denuncia.EM_ANALISE.equals(d.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta denúncia já foi decidida.");
        boolean procede = "procedente".equals(decisao);
        String resposta = body == null || body.resposta() == null || body.resposta().isBlank() ? null : body.resposta().trim();
        d.decidir(procede ? Denuncia.PROCEDENTE : Denuncia.IMPROCEDENTE, adm.id(), resposta, System.currentTimeMillis());
        moderacao.registrar(d.getDenunciadoId(), "denuncia", "Denúncia considerada " + (procede ? "procedente" : "improcedente"), adm.id(), d.getPedidoId());
        if (procede && body != null && Boolean.TRUE.equals(body.aviso())) moderacao.registrarInfracao(d.getDenunciadoId(), "denúncia procedente", d.getPedidoId());
        notificacoes.criar(d.getDenuncianteId(), d.getPedidoId(), "denuncia", "Denúncia analisada",
                (procede ? "Analisamos a sua denúncia e tomamos providências." : "Analisamos a sua denúncia e não encontramos motivo para punir.") + (resposta == null ? "" : " " + resposta), "denuncia:" + d.getId());
        eventos.enviar(d.getDenuncianteId(), "moderacao", null);
        eventos.enviar(d.getDenunciadoId(), "moderacao", null);
        return dto(d, true);
    }

    private Map<String, Object> dto(Denuncia d, boolean completo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId()); m.put("pedidoId", d.getPedidoId()); m.put("motivo", d.getMotivo()); m.put("motivoTexto", MOTIVOS.get(d.getMotivo()));
        m.put("texto", d.getTexto()); m.put("status", d.getStatus()); m.put("criadaEm", d.getCriadaEm()); m.put("decididaEm", d.getDecididaEm()); m.put("resposta", d.getResposta());
        if (completo) {
            m.put("denunciante", resumo(d.getDenuncianteId()));
            m.put("denunciado", resumo(d.getDenunciadoId()));
            m.put("denunciasContraEle", denuncias.countByDenunciadoId(d.getDenunciadoId()));
        } else m.put("denunciado", usuarios.findById(d.getDenunciadoId()).map(Usuario::getNome).orElse("Conta excluída"));
        return m;
    }
    private Map<String, Object> resumo(Long id) {
        return usuarios.findById(id).map(u -> {
            ModeracaoService.Situacao s = moderacao.situacao(u);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", u.getId()); r.put("nome", u.getNome()); r.put("email", u.getEmail()); r.put("tipo", u.getTipo());
            r.put("avisos", s.avisos()); r.put("suspenso", s.suspenso()); r.put("banido", s.banido());
            return r;
        }).orElse(Map.of("id", id, "nome", "Conta excluída"));
    }
    private TaskgoPrincipal admin(Authentication auth) {
        return PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.MODERACAO);
    }
    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
}
