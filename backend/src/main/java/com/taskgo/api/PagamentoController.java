package com.taskgo.api;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {
    private final PagamentoService service;
    private final ModeracaoService moderacao;
    private final String tokenAviso;

    public PagamentoController(PagamentoService service, ModeracaoService moderacao, @Value("${taskgo.pagamentos.pix.webhook-token:}") String tokenAviso) {
        this.service = service; this.moderacao = moderacao; this.tokenAviso = tokenAviso == null ? "" : tokenAviso;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("modo", service.modo(), "taxa", service.taxa(), "teste", service.teste(), "cartaoNaPagina", service.asaasAtivo(), "jurosMes", service.jurosMes(), "modelo", service.modelo());
    }

    @GetMapping("/cartoes")
    public List<PagamentoService.CartaoDto> cartoes(Authentication auth) { return service.listarCartoes(pagador(auth).id()); }

    @PostMapping("/cartoes") @ResponseStatus(HttpStatus.CREATED)
    public PagamentoService.CartaoDto salvar(@RequestBody PagamentoService.NovoCartao novo, Authentication auth) { return service.salvarCartao(pagador(auth).id(), novo); }

    @DeleteMapping("/cartoes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String id, Authentication auth) { service.removerCartao(pagador(auth).id(), id); }

    @PutMapping("/cartoes/{id}/padrao") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void padrao(@PathVariable String id, Authentication auth) { service.tornarPadrao(pagador(auth).id(), id); }

    @GetMapping("/pedidos/{pedidoId}")
    public PagamentoService.ResumoPagamento resumo(@PathVariable String pedidoId, Authentication auth) { return service.resumo(pedidoId, principal(auth)); }

    @PostMapping("/pedidos/{pedidoId}")
    public PagamentoService.CobrancaDto pagar(@PathVariable String pedidoId, @RequestBody PagarRequest req, Authentication auth) {
        TaskgoPrincipal p = cliente(auth);
        moderacao.exigirLiberado(p.id());
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha Pix ou cartão.");
        return service.pagar(pedidoId, p.id(), req.metodo(), req.cartaoId(), req.parcelas());
    }

    @PostMapping("/pedidos/{pedidoId}/confirmar-conclusao") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmarConclusao(@PathVariable String pedidoId, Authentication auth) { service.confirmarConclusao(pedidoId, cliente(auth).id()); }

    @PostMapping("/pedidos/{pedidoId}/problema") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void problema(@PathVariable String pedidoId, @RequestBody(required = false) ProblemaRequest req, Authentication auth) {
        service.abrirProblema(pedidoId, cliente(auth).id(), req == null ? null : req.motivo());
    }

    /* Administrador: decide um problema aberto pelo cliente. */
    @PostMapping("/admin/pedidos/{pedidoId}/{decisao}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decidir(@PathVariable String pedidoId, @PathVariable String decisao, Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.PAGAMENTOS);
        if (!java.util.Set.of("liberar", "devolver").contains(decisao)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisão inválida.");
        service.decidirProblema(pedidoId, "liberar".equals(decisao));
        TaskgoPrincipal quem = (TaskgoPrincipal) auth.getPrincipal();
        moderacao.registrar(quem.id(), "equipe", ("liberar".equals(decisao) ? "Liberou ao profissional" : "Devolveu ao cliente") + " o pagamento do pedido com problema", quem.id(), pedidoId);
    }

    public record ProblemaRequest(String motivo) {}

    @GetMapping("/cobrancas/{id}")
    public PagamentoService.CobrancaDto cobranca(@PathVariable String id, Authentication auth) { return service.consultar(id, pagador(auth).id()); }

    @PostMapping("/cobrancas/{id}/simular-pix")
    public PagamentoService.CobrancaDto simularPix(@PathVariable String id, Authentication auth) { return service.simularPix(id, pagador(auth).id()); }

    /* Aviso do provedor Pix. Sem sessão: vale só com o token combinado; a confirmação é feita consultando o provedor. */
    @PostMapping("/webhook/pix")
    public ResponseEntity<Void> avisoPix(@RequestHeader(value = "X-Webhook-Token", required = false) String token, @RequestBody(required = false) Map<String, Object> corpo) {
        if (tokenAviso.isBlank() || token == null || !MessageDigest.isEqual(tokenAviso.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        service.avisoPix(PagamentoService.txidsDoAviso(corpo));
        return ResponseEntity.ok().build();
    }

    /* Aviso do Asaas (webhook): vale só com o token configurado no painel do Asaas; a confirmação é feita consultando a API. */
    @PostMapping("/webhook/asaas")
    public ResponseEntity<Void> avisoAsaas(@RequestHeader(value = "asaas-access-token", required = false) String token, @RequestBody(required = false) Map<String, Object> corpo) {
        if (tokenAsaas.isBlank() || token == null || !MessageDigest.isEqual(tokenAsaas.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (corpo != null && corpo.get("payment") instanceof Map<?, ?> pg && pg.get("id") instanceof String id) service.avisoAsaas(id);
        return ResponseEntity.ok().build();
    }
    private String tokenAsaas = "";
    @org.springframework.beans.factory.annotation.Autowired
    void setTokenAsaas(@Value("${taskgo.pagamentos.asaas.webhook-token:}") String t) { this.tokenAsaas = t == null ? "" : t; }

    public record PagarRequest(String metodo, String cartaoId, Integer parcelas) {}

    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
    /* Quem paga uma cobrança: o cliente (pedido) ou o profissional (taxa da TaskGO). Cada um só vê as próprias. */
    private TaskgoPrincipal pagador(Authentication a) {
        TaskgoPrincipal p = principal(a);
        if (!java.util.Set.of("cliente", "profissional").contains(String.valueOf(p.tipo()).toLowerCase(java.util.Locale.ROOT))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de cliente ou profissional.");
        return p;
    }
    private TaskgoPrincipal cliente(Authentication a) {
        TaskgoPrincipal p = principal(a);
        if (!"cliente".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só contas de cliente pagam pedidos.");
        return p;
    }
}
