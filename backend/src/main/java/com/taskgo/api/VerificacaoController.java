package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Verificação de identidade: a conta envia documento e selfie; o administrador compara e aprova ou recusa.
   Aprovada, a conta ganha o selo de verificada. As imagens somem assim que a decisão é tomada. */
@RestController
public class VerificacaoController {
    static final String CHAVE = "verificado";
    private final VerificacaoRepository verificacoes;
    private final UsuarioRepository usuarios;
    private final NotificacaoService notificacoes;
    private final ModeracaoService moderacao;

    public VerificacaoController(VerificacaoRepository verificacoes, UsuarioRepository usuarios, NotificacaoService notificacoes, ModeracaoService moderacao) {
        this.verificacoes = verificacoes; this.usuarios = usuarios; this.notificacoes = notificacoes; this.moderacao = moderacao;
    }

    static boolean verificado(Usuario u) { return u != null && Boolean.TRUE.equals(u.getExtra().get(CHAVE)); }

    @GetMapping("/api/verificacao")
    @Transactional(readOnly = true)
    public Map<String, Object> minha(Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Verificacao v = verificacoes.findFirstByUsuarioIdOrderByEnviadaEmDesc(p.id()).orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("verificado", verificado(u));
        r.put("status", verificado(u) ? Verificacao.APROVADA : v == null ? "nenhuma" : v.getStatus());
        r.put("motivo", v != null && Verificacao.RECUSADA.equals(v.getStatus()) ? v.getMotivo() : null);
        r.put("correcao", v != null && Verificacao.RECUSADA.equals(v.getStatus()) ? v.getCorrecao() : null);
        /* Cliente e profissional só usam a TaskGO com a identidade verificada; a equipe não passa por verificação. */
        r.put("obrigatoria", !FotoPedidoService.equipe(p));
        r.put("enviadaEm", v == null ? null : v.getEnviadaEm());
        return r;
    }

    public record Envio(String frente, String verso, String selfie) {}

    @PostMapping("/api/verificacao")
    @Transactional
    public Map<String, Object> enviar(@RequestBody Envio body, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        if (FotoPedidoService.equipe(p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contas da equipe não passam por verificação.");
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (verificado(u)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua conta já está verificada.");
        Verificacao ultima = verificacoes.findFirstByUsuarioIdOrderByEnviadaEmDesc(p.id()).orElse(null);
        if (ultima != null && Verificacao.PENDENTE.equals(ultima.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua verificação já está em análise.");
        if (body == null || body.frente() == null || body.selfie() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a foto do documento e a selfie.");
        byte[] frente = FotoPedidoService.processar(body.frente()).jpeg();
        byte[] verso = body.verso() == null || body.verso().isBlank() ? null : FotoPedidoService.processar(body.verso()).jpeg();
        byte[] selfie = FotoPedidoService.processar(body.selfie()).jpeg();
        verificacoes.save(new Verificacao(UUID.randomUUID().toString(), p.id(), frente, verso, selfie, System.currentTimeMillis()));
        moderacao.registrar(p.id(), "verificacao", "Enviou documento e selfie para verificação", p.id(), null);
        java.util.stream.Stream.of("suporte", "moderador").flatMap(t -> usuarios.findByTipoIgnoreCaseOrderByNomeAsc(t).stream()).forEach(a -> {
            try { notificacoes.criar(a.getId(), null, "conta", "Verificação para analisar", u.getNome() + " enviou documento e selfie. Veja em Administração > Verificações.", "verificacao:" + p.id() + ":" + System.currentTimeMillis() / 60000 + ":" + a.getId()); }
            catch (RuntimeException ignorar) { /* aviso repetido */ }
        });
        return minha(auth);
    }

    /* ---------- administrador ---------- */
    @GetMapping("/api/admin/verificacoes")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendentes(Authentication auth) {
        admin(auth);
        return verificacoes.findByStatusOrderByEnviadaEmAsc(Verificacao.PENDENTE).stream().limit(100).map(v -> {
            Usuario u = usuarios.findById(v.getUsuarioId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId()); m.put("usuarioId", v.getUsuarioId()); m.put("enviadaEm", v.getEnviadaEm()); m.put("temVerso", v.getDocVerso() != null);
            m.put("nome", u == null ? "(conta apagada)" : u.getNome()); m.put("email", u == null ? null : u.getEmail());
            m.put("tipo", u == null ? null : u.getTipo()); m.put("cpf", u == null ? null : u.getCpf()); m.put("nascimento", u == null ? null : u.getBirth());
            return m;
        }).toList();
    }

    @GetMapping("/api/admin/verificacoes/{id}/{parte}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> imagem(@PathVariable String id, @PathVariable String parte, Authentication auth) {
        admin(auth);
        Verificacao v = verificacoes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] b = switch (parte) { case "frente" -> v.getDocFrente(); case "verso" -> v.getDocVerso(); case "selfie" -> v.getSelfie(); default -> null; };
        if (b == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(b);
    }

    public record Decisao(String motivo, String correcao) {}

    @PostMapping("/api/admin/verificacoes/{id}/{decisao}")
    @Transactional
    public Map<String, Object> decidir(@PathVariable String id, @PathVariable String decisao, @RequestBody(required = false) Decisao body, Authentication auth) {
        TaskgoPrincipal a = admin(auth);
        if (!decisao.equals("aprovar") && !decisao.equals("recusar")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisão inválida.");
        Verificacao v = verificacoes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Verificacao.PENDENTE.equals(v.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta verificação já foi decidida.");
        boolean aprovada = decisao.equals("aprovar");
        String motivo = body == null || body.motivo() == null ? null : body.motivo().trim();
        if (!aprovada && (motivo == null || motivo.length() < 5)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diga o motivo da recusa (a pessoa vai ver).");
        if (motivo != null && motivo.length() > 300) motivo = motivo.substring(0, 300);
        String correcao = body == null || body.correcao() == null ? null : body.correcao().trim();
        if (!aprovada && (correcao == null || correcao.length() < 5)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diga como a pessoa corrige (ela vai ver).");
        if (correcao != null && correcao.length() > 500) correcao = correcao.substring(0, 500);
        long agora = System.currentTimeMillis();
        v.decidir(aprovada, aprovada ? null : motivo, aprovada ? null : correcao, a.id(), agora);
        verificacoes.save(v);
        usuarios.findById(v.getUsuarioId()).ifPresent(u -> {
            if (aprovada) { u.getExtra().put(CHAVE, true); u.getExtra().put("verificadoEm", agora); usuarios.save(u); }
            notificacoes.criar(u.getId(), null, "conta", aprovada ? "Identidade verificada" : "Verificação recusada",
                    aprovada ? "Sua conta está liberada para usar a TaskGO." : "Motivo: " + v.getMotivo() + ". Como corrigir: " + v.getCorrecao(), "verificacao-decisao:" + v.getId());
        });
        moderacao.registrar(v.getUsuarioId(), "verificacao", aprovada ? "Identidade verificada" : "Verificação recusada: " + v.getMotivo(), a.id(), null);
        return Map.of("status", v.getStatus());
    }

    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
    private TaskgoPrincipal admin(Authentication a) {
        return PapelEquipe.exigir(a, moderacao, PapelEquipe.Area.MODERACAO);
    }
}
