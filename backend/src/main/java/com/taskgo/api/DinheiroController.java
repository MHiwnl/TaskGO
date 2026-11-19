package com.taskgo.api;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Pagamento em dinheiro, troca de forma de pagamento e pendências. */
@RestController
public class DinheiroController {
    private final DinheiroService service;
    private final ModeracaoService moderacao;
    public DinheiroController(DinheiroService service, ModeracaoService moderacao) { this.service = service; this.moderacao = moderacao; }

    /** Quem atendeu: recebeu em dinheiro (com troco em espécie ou online). */
    @PostMapping("/api/pagamentos/pedidos/{pedidoId}/dinheiro")
    public Map<String, Object> receber(@PathVariable String pedidoId, @RequestBody(required = false) RecebimentoRequest req, Authentication auth) {
        return service.receber(pedidoId, conta(auth), req == null ? null : req.recebido(), req != null && Boolean.TRUE.equals(req.trocoOnline()));
    }

    /** Quem atendeu: o cliente não pagou. */
    @PostMapping("/api/pagamentos/pedidos/{pedidoId}/nao-pago")
    public Pedido naoPago(@PathVariable String pedidoId, Authentication auth) { return service.naoPago(pedidoId, conta(auth)); }

    /** Cliente: troca a forma de pagamento uma vez (dinheiro para cartão, Pix ou carteira, ou o contrário). */
    @PostMapping("/api/pagamentos/pedidos/{pedidoId}/trocar-forma")
    public Object trocarForma(@PathVariable String pedidoId, @RequestBody TrocaRequest req, Authentication auth) {
        TaskgoPrincipal p = conta(auth);
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha a nova forma de pagamento.");
        return service.trocarForma(pedidoId, p.id(), req.metodo(), req.cartaoId());
    }

    /** Cliente: chave Pix de quem atende, para pagar direto. */
    @GetMapping("/api/pagamentos/pedidos/{pedidoId}/chave-pix")
    public Map<String, Object> chave(@PathVariable String pedidoId, Authentication auth) { return service.chaveParaPagar(pedidoId, conta(auth)); }

    /** Quem atendeu: o cliente pagou direto o valor que devia. */
    @PostMapping("/api/pagamentos/pedidos/{pedidoId}/pendencia-recebida")
    public Map<String, Object> pendenciaRecebida(@PathVariable String pedidoId, Authentication auth) { return service.pendenciaRecebida(pedidoId, conta(auth)); }

    /** Quem atende: autoriza (ou desliga) a cobrança automática da taxa num cartão salvo. */
    @org.springframework.web.bind.annotation.PutMapping("/api/pendencias/cobranca-automatica")
    public Map<String, Object> cobrancaAutomatica(@RequestBody AutorizacaoRequest req, Authentication auth) {
        return service.autorizarCobranca(conta(auth).id(), req == null ? null : req.cartaoId(), req != null && Boolean.TRUE.equals(req.autorizado()));
    }

    @GetMapping("/api/pendencias")
    public Map<String, Object> minhas(Authentication auth) { return service.minhas(conta(auth).id()); }

    @PostMapping("/api/pendencias/{id}/pagar")
    public Object pagar(@PathVariable String id, @RequestBody TrocaRequest req, Authentication auth) {
        TaskgoPrincipal p = conta(auth);
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha como pagar.");
        return service.pagarPendencia(id, p.id(), req.metodo(), req.cartaoId());
    }

    private TaskgoPrincipal conta(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!java.util.Set.of("cliente", "profissional").contains(String.valueOf(p.tipo()).toLowerCase(java.util.Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de cliente ou profissional.");
        return p;
    }

    public record RecebimentoRequest(BigDecimal recebido, Boolean trocoOnline) {}
    public record TrocaRequest(String metodo, String cartaoId) {}
    public record AutorizacaoRequest(String cartaoId, Boolean autorizado) {}
}
