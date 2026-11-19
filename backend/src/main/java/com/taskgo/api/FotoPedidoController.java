package com.taskgo.api;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FotoPedidoController {
    private final FotoPedidoService fotos;
    private final EventosService eventos;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    public FotoPedidoController(FotoPedidoService fotos, EventosService eventos, PedidoContratadoRepository pedidos, UsuarioRepository usuarios) {
        this.fotos = fotos; this.eventos = eventos; this.pedidos = pedidos; this.usuarios = usuarios;
    }

    @GetMapping("/api/pedidos/contratados/{pedidoId}/fotos")
    public List<FotoPedidoRepository.Resumo> listar(@PathVariable String pedidoId, Authentication auth) { return fotos.doPedido(pedidoId, principal(auth)); }

    @PostMapping("/api/pedidos/contratados/{pedidoId}/fotos") @ResponseStatus(HttpStatus.CREATED)
    public FotoPedidoRepository.Resumo enviar(@PathVariable String pedidoId, @RequestBody NovaFoto body, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        FotoPedidoRepository.Resumo r = fotos.adicionarAoPedido(pedidoId, p, body == null ? null : body.foto());
        /* Avisa o outro lado para atualizar a tela. */
        pedidos.findById(pedidoId).ifPresent(x -> {
            String outro = p.email().equalsIgnoreCase(String.valueOf(x.getClientEmail())) ? x.getProEmail() : x.getClientEmail();
            if (outro != null) usuarios.findByEmailIgnoreCase(outro).ifPresent(u -> eventos.enviar(u.getId(), "pedido", pedidoId));
        });
        return r;
    }

    @GetMapping("/api/fotos-pedido/{fotoId}")
    public ResponseEntity<byte[]> abrir(@PathVariable String fotoId, Authentication auth) {
        FotoPedido f = fotos.abrir(fotoId, principal(auth));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").header("Content-Disposition", "inline").body(f.getDados());
    }

    @DeleteMapping("/api/fotos-pedido/{fotoId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable String fotoId, Authentication auth) { fotos.apagar(fotoId, principal(auth)); }

    public record NovaFoto(String foto) {}
    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
}
