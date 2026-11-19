package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Liga e desliga a ajuda rápida da própria conta. Ligada, a pessoa recebe chamados de ajuda por perto. */
@RestController
@RequestMapping("/api/ajuda-rapida")
public class AjudaRapidaController {
    private final UsuarioRepository usuarios;
    private final ModeracaoService moderacao;
    public AjudaRapidaController(UsuarioRepository usuarios, ModeracaoService moderacao) { this.usuarios = usuarios; this.moderacao = moderacao; }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> obter(Authentication auth) { return estado(conta(auth)); }

    @PutMapping
    @Transactional
    public Map<String, Object> alterar(@RequestBody Map<String, Object> body, Authentication auth) {
        Usuario u = conta(auth);
        if (body == null || !(body.get("ativo") instanceof Boolean ligar)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ativo: true ou false.");
        if (ligar) {
            if (!VerificacaoController.verificado(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifique sua identidade em Configurações para ajudar.");
            moderacao.exigirLiberado(u.getId());
        }
        Map<String, Object> atual = new LinkedHashMap<>();
        if (u.getExtra().get(AjudaRapida.CHAVE) instanceof Map<?, ?> m) m.forEach((k, v) -> atual.put(String.valueOf(k), v));
        atual.put("ativo", ligar);
        if (ligar) atual.putIfAbsent("primeiraVez", System.currentTimeMillis());
        u.getExtra().put(AjudaRapida.CHAVE, atual);
        usuarios.save(u);
        if (ligar) moderacao.registrar(u.getId(), "conta", "Ligou a ajuda rápida", u.getId(), null);
        return estado(u);
    }

    private static Map<String, Object> estado(Usuario u) {
        return Map.of("ativo", AjudaRapida.ativo(u), "conhece", AjudaRapida.conhece(u), "verificado", VerificacaoController.verificado(u),
                "minimo", AjudaRapida.MINIMO, "taxa", AjudaRapida.TAXA);
    }

    private Usuario conta(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!java.util.Set.of("cliente", "profissional").contains(String.valueOf(p.tipo()).toLowerCase(java.util.Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de cliente ou profissional.");
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
