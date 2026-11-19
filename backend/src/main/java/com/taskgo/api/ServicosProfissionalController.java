package com.taskgo.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* O profissional escolhe as categorias que sabe fazer (a primeira é a principal). */
@RestController
public class ServicosProfissionalController {
    private final ServicosProfissional servicos;
    private final UsuarioRepository usuarios;
    public ServicosProfissionalController(ServicosProfissional servicos, UsuarioRepository usuarios) { this.servicos = servicos; this.usuarios = usuarios; }

    @GetMapping("/api/profissionais/eu/servicos")
    public Map<String, Object> meus(Authentication auth) { Usuario u = pro(auth); return Map.of("servicos", servicos.de(u), "maximo", servicos.maximo(u)); }

    public record Servicos(List<String> servicos) {}
    @PutMapping("/api/profissionais/eu/servicos")
    public Map<String, Object> salvar(@RequestBody Servicos body, Authentication auth) {
        Usuario u = pro(auth);
        return Map.of("servicos", servicos.salvar(u, body == null ? null : body.servicos()), "maximo", servicos.maximo(u));
    }

    private Usuario pro(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!"profissional".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para profissionais.");
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
