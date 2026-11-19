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

/* Área de atendimento do profissional: o raio em km e o ponto de partida (onde ele costuma sair).
   Quando ele está online com a posição recente, a distância conta a partir de onde ele está. */
@RestController
@RequestMapping("/api/profissionais/eu/area")
public class AreaAtendimentoController {
    static final String CHAVE = "areaAtendimento";
    static final double RAIO_PADRAO = 15, RAIO_MIN = 1, RAIO_MAX = 50;
    private final UsuarioRepository usuarios;
    private final PresencaProfissionalRepository presencas;

    public AreaAtendimentoController(UsuarioRepository usuarios, PresencaProfissionalRepository presencas) { this.usuarios = usuarios; this.presencas = presencas; }

    public record Area(double raioKm, Double baseLat, Double baseLng, String baseTexto) {}
    public record Ponto(double lat, double lng, String origem) {}

    static Area area(Usuario u) {
        if (u.getExtra().get(CHAVE) instanceof Map<?, ?> m) {
            double raio = m.get("raioKm") instanceof Number n ? n.doubleValue() : RAIO_PADRAO;
            return new Area(raio, ChamarAgoraService.numero(m.get("baseLat")), ChamarAgoraService.numero(m.get("baseLng")), m.get("baseTexto") instanceof String s ? s : null);
        }
        return new Area(RAIO_PADRAO, null, null, null);
    }

    /* De onde medir a distância: a posição recente (online) ou o ponto de partida salvo. */
    static Ponto ponto(Usuario u, PresencaProfissionalRepository presencas) {
        if (presencas != null) {
            var p = presencas.findById(u.getId()).orElse(null);
            if (p != null && System.currentTimeMillis() - p.getAtualizadaEm() < ChamarAgoraService.PRESENCA_VALIDA_MS)
                return new Ponto(p.getLatitude(), p.getLongitude(), "agora");
        }
        Area a = area(u);
        return a.baseLat() == null || a.baseLng() == null ? null : new Ponto(a.baseLat(), a.baseLng(), "base");
    }

    /* Distância do profissional até o pedido; null quando um dos dois não tem ponto. */
    static Double distancia(Ponto de, Pedido p) {
        Double lat = ChamarAgoraService.numero(p.getExtra().get("lat")), lng = ChamarAgoraService.numero(p.getExtra().get("lng"));
        if (de == null || lat == null || lng == null) return null;
        return ChamarAgoraService.distanciaKm(de.lat(), de.lng(), lat, lng);
    }

    /* Dentro da área: sem ponto de um dos lados, não dá para filtrar, e o pedido aparece. */
    static boolean dentro(Usuario u, Ponto de, Pedido p) {
        Double km = distancia(de, p);
        return km == null || km <= area(u).raioKm();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> obter(Authentication auth) { return resposta(profissional(auth)); }

    @PutMapping
    @Transactional
    public Map<String, Object> salvar(@RequestBody Map<String, Object> body, Authentication auth) {
        Usuario u = profissional(auth);
        if (body == null || !(body.get("raioKm") instanceof Number r)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o raio em km.");
        double raio = Math.round(r.doubleValue());
        if (raio < RAIO_MIN || raio > RAIO_MAX) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O raio vai de 1 a 50 km.");
        Double lat = ChamarAgoraService.numero(body.get("baseLat")), lng = ChamarAgoraService.numero(body.get("baseLng"));
        if ((lat == null) != (lng == null) || lat != null && (Math.abs(lat) > 90 || Math.abs(lng) > 180))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ponto de partida inválido. Marque de novo no mapa.");
        String texto = body.get("baseTexto") instanceof String s ? s.trim() : null;
        if (texto != null && texto.length() > 120) texto = texto.substring(0, 120);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raioKm", raio);
        if (lat != null) { m.put("baseLat", lat); m.put("baseLng", lng); }
        if (texto != null && !texto.isBlank()) m.put("baseTexto", texto);
        u.getExtra().put(CHAVE, m);
        usuarios.save(u);
        return resposta(u);
    }

    private Map<String, Object> resposta(Usuario u) {
        Area a = area(u);
        Ponto p = ponto(u, presencas);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("raioKm", a.raioKm());
        r.put("baseLat", a.baseLat()); r.put("baseLng", a.baseLng()); r.put("baseTexto", a.baseTexto());
        r.put("medindoDe", p == null ? null : p.origem());
        return r;
    }

    private Usuario profissional(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!"profissional".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de profissional.");
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
