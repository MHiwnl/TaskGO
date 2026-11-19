package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agenda")
public class AgendaController {
    private final AgendaService agenda;
    private final UsuarioRepository usuarios;
    public AgendaController(AgendaService agenda, UsuarioRepository usuarios) { this.agenda = agenda; this.usuarios = usuarios; }

    /* Atendimentos marcados e bloqueios entre duas datas (no máximo 62 dias). */
    @GetMapping
    public Map<String, Object> ver(@RequestParam long de, @RequestParam long ate, Authentication auth) {
        Usuario pro = profissional(auth);
        if (ate <= de || ate - de > 62L * 24 * 60 * 60 * 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido.");
        List<Map<String, Object>> atendimentos = agenda.agendados(pro.getEmail()).stream()
                .filter(p -> p.getSched() < ate && p.getSched() + AgendaService.DURACAO_MS > de)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId()); m.put("servico", p.getService()); m.put("inicio", p.getSched()); m.put("fim", p.getSched() + AgendaService.DURACAO_MS);
                    m.put("status", p.getStatus()); m.put("valor", p.getPrice()); m.put("bairro", p.getExtra().get("bairro"));
                    return m;
                }).toList();
        List<Map<String, Object>> bloqueios = agenda.bloqueiosEntre(pro.getId(), de, ate).stream()
                .map(b -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", b.getId()); m.put("inicio", b.getInicio()); m.put("fim", b.getFim()); m.put("motivo", b.getMotivo()); return m; }).toList();
        return Map.of("atendimentos", atendimentos, "bloqueios", bloqueios, "duracaoMs", AgendaService.DURACAO_MS);
    }

    @PostMapping("/bloqueios") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> bloquear(@RequestBody NovoBloqueio body, Authentication auth) {
        if (body == null || body.inicio() == null || body.fim() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o início e o fim.");
        BloqueioAgenda b = agenda.bloquear(profissional(auth), body.inicio(), body.fim(), body.motivo());
        return Map.of("id", b.getId(), "inicio", b.getInicio(), "fim", b.getFim(), "motivo", b.getMotivo() == null ? "" : b.getMotivo());
    }

    @DeleteMapping("/bloqueios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@PathVariable String id, Authentication auth) { agenda.desbloquear(profissional(auth).getId(), id); }

    public record NovoBloqueio(Long inicio, Long fim, String motivo) {}

    private Usuario profissional(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!"profissional".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de profissional.");
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
