package com.taskgo.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Plano Pro do profissional: situação, assinar, pagar o mês, trocar a forma e cancelar a renovação. */
@RestController
@RequestMapping("/api/plano")
public class PlanoController {
    private final PlanoService planos;
    private final UsuarioRepository usuarios;
    public PlanoController(PlanoService planos, UsuarioRepository usuarios) { this.planos = planos; this.usuarios = usuarios; }

    public record Pedido(String forma, String metodo, String cartaoId) {}

    @GetMapping
    public Map<String, Object> situacao(Authentication auth) { return planos.situacao(usuario(auth)); }

    @PostMapping("/assinar")
    public Map<String, Object> assinar(@RequestBody Pedido body, Authentication auth) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha cartão ou Pix.");
        return planos.assinar(id(auth), body.forma(), body.metodo(), body.cartaoId());
    }

    @PostMapping("/pagar")
    public Map<String, Object> pagar(@RequestBody Pedido body, Authentication auth) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha como pagar.");
        return planos.pagar(id(auth), body.metodo(), body.cartaoId());
    }

    @PutMapping("/forma")
    public Map<String, Object> forma(@RequestBody Pedido body, Authentication auth) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha cartão ou Pix.");
        return planos.trocarForma(id(auth), body.forma(), body.cartaoId());
    }

    @PostMapping("/cancelar")
    public Map<String, Object> cancelar(Authentication auth) { return planos.cancelar(id(auth)); }

    @PostMapping("/desistir")
    public Map<String, Object> desistir(Authentication auth) { return planos.desistir(id(auth)); }

    private Long id(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p.id();
    }
    private Usuario usuario(Authentication auth) { return usuarios.findById(id(auth)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
}
