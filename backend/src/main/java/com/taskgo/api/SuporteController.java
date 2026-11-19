package com.taskgo.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

/* Emergência: o botão do pedido abre um chamado que chega na hora, por eventos ao vivo, a toda conta de suporte.
   Contas de suporte são os e-mails de TASKGO_SUPORTE_EMAILS (tipo próprio, como o administrador). */
@RestController
public class SuporteController {
    private static final Logger log = LoggerFactory.getLogger(SuporteController.class);
    private final ChamadoRepository chamados;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final LocalizacaoPedidoRepository localizacoes;
    private final EventosService eventos;
    private final ModeracaoService moderacao;
    private final EmailRecuperacaoSenha email;
    private final Set<String> suporteEmails;

    public SuporteController(ChamadoRepository chamados, PedidoContratadoRepository pedidos, UsuarioRepository usuarios,
                             LocalizacaoPedidoRepository localizacoes, EventosService eventos, ModeracaoService moderacao,
                             EmailRecuperacaoSenha email, @Value("${taskgo.suporte.emails:}") String suporte) {
        this.chamados = chamados; this.pedidos = pedidos; this.usuarios = usuarios; this.localizacoes = localizacoes;
        this.eventos = eventos; this.moderacao = moderacao; this.email = email;
        this.suporteEmails = Arrays.stream(suporte.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public boolean suporte(String endereco) { return endereco != null && suporteEmails.contains(endereco.trim().toLowerCase(Locale.ROOT)); }

    public record Emergencia(String pedidoId, String mensagem, Double latitude, Double longitude) {}

    @PostMapping("/api/suporte/emergencia")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> emergencia(@RequestBody Emergencia body, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        if (body == null || body.pedidoId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o pedido.");
        Pedido pedido = pedidos.findById(body.pedidoId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!p.email().equalsIgnoreCase(String.valueOf(pedido.getClientEmail())) && !p.email().equalsIgnoreCase(String.valueOf(pedido.getProEmail())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só quem está no pedido pode chamar o suporte por ele.");
        /* Pedido encerrado: a emergência é do atendimento; depois dele, o caminho é denunciar. */
        if (java.util.Set.of("concluido", "cancelado").contains(pedido.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido já terminou. Se algo aconteceu, use Denunciar.");
        Double lat = valido(body.latitude(), 90) ? body.latitude() : null, lng = lat != null && valido(body.longitude(), 180) ? body.longitude() : null;
        if (lng == null) lat = null;
        String msg = body.mensagem() == null || body.mensagem().isBlank() ? null : body.mensagem().trim();
        if (msg != null && msg.length() > 1000) msg = msg.substring(0, 1000);
        /* Um chamado aberto por pedido: apertar de novo só atualiza a posição e a mensagem. */
        Chamado c = chamados.findByAutorIdAndStatusNot(p.id(), Chamado.RESOLVIDO).stream().filter(x -> pedido.getId().equals(x.getPedidoId())).findFirst().orElse(null);
        boolean novo = c == null;
        if (novo) c = new Chamado(pedido.getId(), p.id(), "emergencia", msg, lat, lng, System.currentTimeMillis());
        else if (lat != null) c.atualizarPosicao(lat, lng);
        c = chamados.save(c);
        avisarSuporte(c, novo);
        if (novo) moderacao.registrar(p.id(), "suporte", "Abriu um chamado de emergência", p.id(), pedido.getId());
        return dto(c, false);
    }

    @GetMapping("/api/suporte/meus-chamados")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> meus(Authentication auth) {
        return chamados.findByAutorIdOrderByCriadoEmDesc(principal(auth).id()).stream().map(c -> dto(c, false)).toList();
    }

    /* ---------- Equipe de suporte (e administrador) ---------- */
    @GetMapping("/api/suporte/chamados")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> todos(Authentication auth) {
        equipe(auth);
        return chamados.findAllByOrderByCriadoEmDesc().stream().map(c -> dto(c, true)).toList();
    }

    @PostMapping("/api/suporte/chamados/{id}/assumir")
    @Transactional
    public Map<String, Object> assumir(@PathVariable String id, Authentication auth) {
        TaskgoPrincipal s = equipe(auth);
        Chamado c = chamados.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Chamado.ABERTO.equals(c.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este chamado já foi assumido.");
        c.assumir(s.id(), System.currentTimeMillis());
        chamados.save(c);
        eventos.enviar(c.getAutorId(), "chamado", c.getId());
        suporteIds().forEach(u -> eventos.enviar(u, "chamado", c.getId()));
        return dto(c, true);
    }

    public record Resolucao(String nota) {}

    @PostMapping("/api/suporte/chamados/{id}/resolver")
    @Transactional
    public Map<String, Object> resolver(@PathVariable String id, @RequestBody(required = false) Resolucao body, Authentication auth) {
        TaskgoPrincipal s = equipe(auth);
        Chamado c = chamados.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (Chamado.RESOLVIDO.equals(c.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este chamado já foi resolvido.");
        if (c.getAssumidoPor() == null) c.assumir(s.id(), System.currentTimeMillis());
        c.resolver(body == null || body.nota() == null || body.nota().isBlank() ? null : body.nota().trim(), System.currentTimeMillis());
        chamados.save(c);
        eventos.enviar(c.getAutorId(), "chamado", c.getId());
        suporteIds().forEach(u -> eventos.enviar(u, "chamado", c.getId()));
        return dto(c, true);
    }

    private void avisarSuporte(Chamado c, boolean novo) {
        List<Long> ids = suporteIds();
        ids.forEach(u -> eventos.enviar(u, novo ? "emergencia" : "chamado", c.getId()));
        if (!novo || !email.canalPronto()) return;
        Pedido p = pedidos.findById(String.valueOf(c.getPedidoId())).orElse(null);
        for (String destino : suporteEmails) {
            try { email.enviarTexto(destino, "EMERGÊNCIA TaskGO: chamado aberto agora",
                    "Um chamado de emergência foi aberto" + (p == null ? "" : " no pedido de " + p.getService() + " (" + p.getId() + ")") + ".\n"
                            + (c.getMensagem() == null ? "" : "Mensagem: " + c.getMensagem() + "\n")
                            + (c.getLatitude() == null ? "" : "Localização: https://www.openstreetmap.org/?mlat=" + c.getLatitude() + "&mlon=" + c.getLongitude() + "#map=17/" + c.getLatitude() + "/" + c.getLongitude() + "\n")
                            + "\nAbra a área de suporte da TaskGO para assumir o chamado."); }
            catch (RuntimeException falha) { log.warn("Não foi possível avisar o suporte por e-mail sobre o chamado {}.", c.getId()); }
        }
    }

    private List<Long> suporteIds() {
        return usuarios.findByTipoIgnoreCaseOrderByNomeAsc("suporte").stream().map(Usuario::getId).toList();
    }

    private Map<String, Object> dto(Chamado c, boolean completo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId()); m.put("pedidoId", c.getPedidoId()); m.put("tipo", c.getTipo()); m.put("status", c.getStatus());
        m.put("mensagem", c.getMensagem()); m.put("criadoEm", c.getCriadoEm()); m.put("assumidoEm", c.getAssumidoEm()); m.put("resolvidoEm", c.getResolvidoEm()); m.put("nota", c.getNota());
        m.put("atendente", c.getAssumidoPor() == null ? null : usuarios.findById(c.getAssumidoPor()).map(Usuario::getNome).orElse("Suporte"));
        if (!completo) return m;
        m.put("latitude", c.getLatitude()); m.put("longitude", c.getLongitude());
        m.put("autor", pessoa(usuarios.findById(c.getAutorId()).orElse(null)));
        pedidos.findById(String.valueOf(c.getPedidoId())).ifPresent(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("servico", p.getService()); pm.put("status", p.getStatus()); pm.put("endereco", p.getAddress()); pm.put("bairro", p.getExtra().get("bairro"));
            pm.put("cliente", pessoa(p.getClientEmail() == null ? null : usuarios.findByEmailIgnoreCase(p.getClientEmail()).orElse(null)));
            pm.put("profissional", pessoa(p.getProEmail() == null ? null : usuarios.findByEmailIgnoreCase(p.getProEmail()).orElse(null)));
            localizacoes.findById(p.getId()).ifPresent(l -> pm.put("posicaoProfissional", Map.of("latitude", l.getLatitude(), "longitude", l.getLongitude(), "medidaEm", l.getMeasuredAt())));
            m.put("pedido", pm);
        });
        return m;
    }
    private static Map<String, Object> pessoa(Usuario u) {
        if (u == null) return null;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId()); r.put("nome", u.getNome()); r.put("telefone", u.getTelefone()); r.put("email", u.getEmail()); r.put("tipo", u.getTipo());
        return r;
    }
    private static boolean valido(Double v, double limite) { return v != null && Double.isFinite(v) && Math.abs(v) <= limite; }
    private TaskgoPrincipal equipe(Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        if (!"suporte".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para a equipe de suporte.");
        return p;
    }
    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
}
