package com.taskgo.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Obras: orçamento com etapas, visita técnica, pagamento por etapa, diário, aditivos, garantia e decisões da equipe. */
@RestController
public class ObraController {
    private final ObraService obras;
    private final ModeracaoService moderacao;
    private final UsuarioRepository usuarios;
    public ObraController(ObraService obras, ModeracaoService moderacao, UsuarioRepository usuarios) { this.obras = obras; this.moderacao = moderacao; this.usuarios = usuarios; }

    @GetMapping("/api/obras/{pedidoId}")
    public Map<String, Object> ver(@PathVariable String pedidoId, Authentication auth) { return obras.ver(pedidoId, conta(auth)); }

    /* ---- orçamento ---- */
    @PostMapping("/api/obras/{pedidoId}/orcamentos")
    public Map<String, Object> enviar(@PathVariable String pedidoId, @RequestBody ObraService.NovoOrcamento body, Authentication auth) { return obras.enviarOrcamento(pedidoId, conta(auth), body); }
    @PostMapping("/api/obras/orcamentos/{id}/retirar")
    public Map<String, Object> retirar(@PathVariable String id, Authentication auth) { return obras.retirarOrcamento(id, conta(auth)); }
    @PostMapping("/api/obras/orcamentos/{id}/recusar")
    public Map<String, Object> recusar(@PathVariable String id, Authentication auth) { return obras.recusarOrcamento(id, conta(auth)); }
    @PostMapping("/api/obras/orcamentos/{id}/aceitar")
    public Map<String, Object> aceitar(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        return obras.aceitarOrcamento(id, conta(auth), body != null && Boolean.TRUE.equals(body.get("contratoAceito")));
    }
    @GetMapping(value = "/api/obras/orcamentos/{id}/contrato", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String contrato(@PathVariable String id, Authentication auth) { return obras.contrato(id, conta(auth)); }

    /* ---- visita técnica ---- */
    public record NovaVisita(BigDecimal valor, Long data) {}
    @PostMapping("/api/obras/{pedidoId}/visitas")
    public Map<String, Object> proporVisita(@PathVariable String pedidoId, @RequestBody NovaVisita body, Authentication auth) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe valor e data.");
        return obras.proporVisita(pedidoId, conta(auth), body.valor(), body.data());
    }
    @PostMapping("/api/obras/visitas/{id}/pagar")
    public Object pagarVisita(@PathVariable String id, @RequestBody Pagamento body, Authentication auth) { return obras.pagarVisita(id, conta(auth), body == null ? null : body.metodo(), body == null ? null : body.cartaoId()); }
    @PostMapping("/api/obras/visitas/{id}/{acao}")
    public Map<String, Object> visita(@PathVariable String id, @PathVariable String acao, Authentication auth) { return obras.responderVisita(id, conta(auth), acao); }

    /* ---- etapas ---- */
    public record Pagamento(String metodo, String cartaoId, Integer parcelas) {}
    @PostMapping("/api/obras/etapas/{id}/pagar")
    public Object pagarEtapa(@PathVariable String id, @RequestBody Pagamento body, Authentication auth) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha como pagar.");
        return obras.pagarEtapa(id, conta(auth), body.metodo(), body.cartaoId(), body.parcelas());
    }
    @PostMapping("/api/obras/etapas/{id}/recebida")
    public Map<String, Object> etapaRecebida(@PathVariable String id, Authentication auth) { return obras.etapaRecebida(id, conta(auth)); }
    @PostMapping("/api/obras/etapas/{id}/iniciar")
    public Map<String, Object> iniciar(@PathVariable String id, Authentication auth) { return obras.iniciarEtapa(id, conta(auth)); }
    public record Material(BigDecimal valor, String foto) {}
    @PostMapping("/api/obras/etapas/{id}/material")
    public Map<String, Object> material(@PathVariable String id, @RequestBody Material body, Authentication auth) { return obras.adiantarMaterial(id, conta(auth), body == null ? null : body.valor(), body == null ? null : body.foto()); }
    public record Entrega(List<String> fotos) {}
    @PostMapping("/api/obras/etapas/{id}/entregar")
    public Map<String, Object> entregar(@PathVariable String id, @RequestBody Entrega body, Authentication auth) { return obras.entregarEtapa(id, conta(auth), body == null ? null : body.fotos()); }
    @PostMapping("/api/obras/etapas/{id}/aprovar")
    public Map<String, Object> aprovar(@PathVariable String id, Authentication auth) { return obras.aprovarEtapa(id, conta(auth)); }
    public record Texto(String texto) {}
    @PostMapping("/api/obras/etapas/{id}/problema")
    public Map<String, Object> problema(@PathVariable String id, @RequestBody Texto body, Authentication auth) { return obras.problemaEtapa(id, conta(auth), body == null ? null : body.texto()); }

    /* ---- diário e aditivos ---- */
    public record Chegada(Double latitude, Double longitude) {}
    @PostMapping("/api/obras/{pedidoId}/diario/chegada")
    public Map<String, Object> chegada(@PathVariable String pedidoId, @RequestBody(required = false) Chegada body, Authentication auth) {
        return obras.chegada(pedidoId, conta(auth), body == null ? null : body.latitude(), body == null ? null : body.longitude());
    }
    public record Saida(String texto, String foto) {}
    @PostMapping("/api/obras/{pedidoId}/diario/saida")
    public Map<String, Object> saida(@PathVariable String pedidoId, @RequestBody Saida body, Authentication auth) { return obras.saida(pedidoId, conta(auth), body == null ? null : body.texto(), body == null ? null : body.foto()); }
    public record Aditivo(String tipo, Integer dias, BigDecimal valor, String nome, String motivo) {}
    @PostMapping("/api/obras/{pedidoId}/aditivos")
    public Map<String, Object> aditivo(@PathVariable String pedidoId, @RequestBody Aditivo b, Authentication auth) {
        if (b == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Descreva o aditivo.");
        return obras.proporAditivo(pedidoId, conta(auth), b.tipo(), b.dias(), b.valor(), b.nome(), b.motivo());
    }
    @PostMapping("/api/obras/{pedidoId}/aditivos/{id}/{resposta}")
    public Map<String, Object> responderAditivo(@PathVariable String pedidoId, @PathVariable String id, @PathVariable String resposta, Authentication auth) {
        if (!java.util.Set.of("aceitar", "recusar").contains(resposta)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resposta inválida.");
        return obras.responderAditivo(pedidoId, id, conta(auth), "aceitar".equals(resposta));
    }
    @PostMapping("/api/obras/{pedidoId}/defeito")
    public Map<String, Object> defeito(@PathVariable String pedidoId, @RequestBody Texto body, Authentication auth) { return obras.defeito(pedidoId, conta(auth), body == null ? null : body.texto()); }
    @PostMapping("/api/obras/{pedidoId}/cancelar")
    public Pedido cancelar(@PathVariable String pedidoId, @RequestBody(required = false) Texto body, Authentication auth) { return obras.cancelar(pedidoId, conta(auth), body == null ? null : body.texto()); }

    /* ---- CNPJ/MEI do profissional (libera orçamentos a partir de R$ 5.000) ---- */
    @GetMapping("/api/obras/eu/cnpj")
    public Map<String, Object> meuCnpj(Authentication auth) {
        Usuario u = usuarios.findById(conta(auth).id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("cnpj", u.getExtra().get("cnpj") instanceof String c ? c : "");
    }
    @PutMapping("/api/obras/eu/cnpj")
    public Map<String, Object> salvarCnpj(@RequestBody Map<String, String> body, Authentication auth) {
        TaskgoPrincipal p = conta(auth);
        if (!"profissional".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para profissionais.");
        String d = body == null || body.get("cnpj") == null ? "" : body.get("cnpj").replaceAll("\\D", "");
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (d.isEmpty()) u.getExtra().remove("cnpj");
        else { if (!ResgateService.cnpjValido(d)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ inválido. Confira os 14 números."); u.getExtra().put("cnpj", d); }
        usuarios.save(u);
        return Map.of("cnpj", d);
    }

    /* ---- equipe ---- */
    @GetMapping("/api/admin/obras")
    public List<Map<String, Object>> fila(Authentication auth) { PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS); return obras.fila(); }
    public record Revisao(Boolean aprovar, String motivo) {}
    @PostMapping("/api/admin/obras/{pedidoId}/revisao")
    public Map<String, Object> revisar(@PathVariable String pedidoId, @RequestBody Revisao body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS);
        obras.revisar(pedidoId, body != null && Boolean.TRUE.equals(body.aprovar()), body == null ? null : body.motivo(), a.id());
        return Map.of("ok", true);
    }
    public record Decisao(BigDecimal aoProfissional) {}
    @PostMapping("/api/admin/obras/etapas/{id}/decidir")
    public Map<String, Object> decidirEtapa(@PathVariable String id, @RequestBody Decisao body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS);
        return obras.decidirEtapa(id, body == null ? null : body.aoProfissional(), a.id());
    }
    @PostMapping("/api/admin/obras/{pedidoId}/garantia")
    public Map<String, Object> decidirGarantia(@PathVariable String pedidoId, @RequestBody Decisao body, Authentication auth) {
        TaskgoPrincipal a = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS);
        return obras.decidirGarantia(pedidoId, body == null ? null : body.aoProfissional(), a.id());
    }

    private TaskgoPrincipal conta(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
}
