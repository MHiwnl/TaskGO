package com.taskgo.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Serviço recorrente: as vezes do pedido, o pagamento por semana e a confirmação de cada vez. */
@RestController
public class RecorrenteController {
    private final RecorrenteService recorrentes;
    private final ModeracaoService moderacao;
    public RecorrenteController(RecorrenteService recorrentes, ModeracaoService moderacao) { this.recorrentes = recorrentes; this.moderacao = moderacao; }

    @GetMapping("/api/recorrentes/{pedidoId}")
    public Map<String, Object> ver(@PathVariable String pedidoId, Authentication auth) { return recorrentes.ver(pedidoId, conta(auth)); }

    public record Pagar(String metodo, String cartaoId) {}
    @PostMapping("/api/recorrentes/{pedidoId}/semanas/{semana}/pagar")
    public Object pagar(@PathVariable String pedidoId, @PathVariable int semana, @RequestBody Pagar body, Authentication auth) {
        TaskgoPrincipal p = conta(auth);
        moderacao.exigirLiberado(p.id());
        return recorrentes.pagarSemana(pedidoId, semana, p, body == null ? null : body.metodo(), body == null ? null : body.cartaoId());
    }

    @PostMapping("/api/recorrentes/vezes/{id}/{acao}")
    public Map<String, Object> agir(@PathVariable String id, @PathVariable String acao, Authentication auth) { return recorrentes.agir(id, conta(auth), acao); }

    /* ---- suporte ---- */
    @GetMapping("/api/admin/recorrentes")
    public List<Map<String, Object>> contestadas(Authentication auth) { PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS); return recorrentes.contestadas(); }
    public record Decisao(Boolean aoProfissional) {}
    @PostMapping("/api/admin/recorrentes/vezes/{id}/decidir")
    public Map<String, Object> decidir(@PathVariable String id, @RequestBody Decisao body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS);
        if (body == null || body.aoProfissional() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diga para quem vai o valor.");
        return recorrentes.decidir(id, body.aoProfissional(), a.id());
    }

    private TaskgoPrincipal conta(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
}
