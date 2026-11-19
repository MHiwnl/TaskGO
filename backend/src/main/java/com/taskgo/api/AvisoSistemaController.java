package com.taskgo.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Avisos do sistema: o administrador escreve uma vez e o aviso chega para todas as contas, sem exceção. */
@RestController
@RequestMapping("/api/admin/avisos")
public class AvisoSistemaController {
    private final ModeracaoService moderacao;
    private final UsuarioRepository usuarios;
    private final NotificacaoRepository notificacoes;
    private final EventosService eventos;

    public AvisoSistemaController(ModeracaoService moderacao, UsuarioRepository usuarios, NotificacaoRepository notificacoes, EventosService eventos) {
        this.moderacao = moderacao; this.usuarios = usuarios; this.notificacoes = notificacoes; this.eventos = eventos;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> enviar(@RequestBody AvisoRequest body, Authentication auth) {
        TaskgoPrincipal p = admin(auth);
        String titulo = body == null || body.titulo() == null ? "" : body.titulo().trim();
        String texto = body == null || body.texto() == null ? "" : body.texto().trim();
        if (titulo.length() < 3 || titulo.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escreva um título de 3 a 120 caracteres.");
        if (texto.length() < 5 || texto.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escreva o aviso com 5 a 2000 caracteres.");
        String chave = "sistema:" + UUID.randomUUID();
        long agora = System.currentTimeMillis();
        List<Notificacao> lote = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Usuario u : usuarios.findAll()) {
            lote.add(new Notificacao(UUID.randomUUID().toString(), u.getId(), null, chave, "sistema", titulo, texto, agora));
            ids.add(u.getId());
        }
        notificacoes.saveAll(lote);
        moderacao.registrar(p.id(), "aviso_sistema", "Enviou o aviso \"" + titulo + "\" para " + ids.size() + " contas", p.id(), null);
        eventos.enviarParaTodos(ids, "notificacao", null);
        return Map.of("chave", chave, "contas", ids.size());
    }

    /** Histórico dos avisos enviados: um por aviso, com quantas contas receberam e quantas já leram. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Authentication auth) {
        admin(auth);
        Map<String, Map<String, Object>> porChave = new LinkedHashMap<>();
        for (Notificacao n : notificacoes.findByTipoOrderByCriadaEmDesc("sistema")) {
            Map<String, Object> m = porChave.computeIfAbsent(n.getChaveEvento(), k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("titulo", n.getTitulo()); x.put("texto", n.getConteudo()); x.put("enviadoEm", n.getCriadaEm());
                x.put("contas", 0); x.put("lidas", 0);
                return x;
            });
            m.put("contas", (int) m.get("contas") + 1);
            if (n.getLidaEm() != null) m.put("lidas", (int) m.get("lidas") + 1);
        }
        return new ArrayList<>(porChave.values());
    }

    private TaskgoPrincipal admin(Authentication auth) {
        return PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.AVISOS);
    }

    public record AvisoRequest(String titulo, String texto) {}
}
