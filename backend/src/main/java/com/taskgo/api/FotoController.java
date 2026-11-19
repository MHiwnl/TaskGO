package com.taskgo.api;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Foto de perfil entre cliente e profissional.
 * A foto do profissional pode ser vista por qualquer conta logada; a do cliente,
 * só por profissionais, pela equipe e pelo próprio cliente (cliente não vê foto de outro cliente).
 */
@RestController
public class FotoController {
    private final UsuarioRepository usuarios;
    public FotoController(UsuarioRepository usuarios) { this.usuarios = usuarios; }

    /** A foto fica nos dados da conta como data URL (data:image/jpeg;base64,...). */
    static String foto(Usuario u) {
        if (u == null) return null;
        if (u.getExtra().get("taskgoData") instanceof Map<?, ?> dados && dados.get("profile") instanceof Map<?, ?> perfil
                && perfil.get("photo") instanceof String s && s.startsWith("data:image/")) return s;
        return null;
    }

    @GetMapping("/api/fotos/{usuarioId}")
    public ResponseEntity<byte[]> obter(@PathVariable Long usuarioId, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal eu)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Usuario alvo = usuarios.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean alvoCliente = "cliente".equalsIgnoreCase(alvo.getTipo());
        if (alvoCliente && "cliente".equalsIgnoreCase(eu.tipo()) && !eu.id().equals(usuarioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String dataUrl = foto(alvo);
        if (dataUrl == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        int virgula = dataUrl.indexOf(',');
        String tipo = dataUrl.substring(5, dataUrl.indexOf(';') > 0 ? dataUrl.indexOf(';') : virgula);
        if (!tipo.matches("image/(jpeg|png|webp|gif)")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(dataUrl.substring(virgula + 1)); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(tipo))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
