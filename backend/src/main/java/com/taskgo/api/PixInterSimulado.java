package com.taskgo.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/* Simulador da API Pix do Banco Inter (pagamentos/pix-simulador). Mesmos caminhos e campos da API real (/pix/v2/cob). */
@Component
public class PixInterSimulado implements ProvedorPix {
    private final RestClient http;

    public PixInterSimulado(@Value("${taskgo.pagamentos.pix.url:http://pix:3100}") String url) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000); f.setReadTimeout(8000);
        this.http = RestClient.builder().baseUrl(url).requestFactory(f).build();
    }

    @Override public String nome() { return "inter-simulado"; }

    @Override
    @SuppressWarnings("unchecked")
    public CobrancaPix criar(String txid, BigDecimal valor, String descricao, int expiracaoSegundos) {
        Map<String, Object> corpo = Map.of(
                "calendario", Map.of("expiracao", expiracaoSegundos),
                "valor", Map.of("original", valor.toPlainString()),
                "infoAdicionais", List.of(Map.of("nome", "Pedido", "valor", descricao)));
        try {
            Map<String, Object> r = http.put().uri(u -> u.path("/pix/v2/cob").queryParam("txid", txid).build())
                    .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(Map.class);
            if (r == null || !(r.get("pixCopiaECola") instanceof String copia)) throw new ProvedorIndisponivel();
            return new CobrancaPix(copia, r.get("qrCodeImage") instanceof String q ? q : null);
        } catch (RestClientException e) { throw new ProvedorIndisponivel(); }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String status(String txid) {
        try {
            Map<String, Object> r = http.get().uri("/pix/v2/cob/{txid}", txid).retrieve().body(Map.class);
            return r == null ? null : String.valueOf(r.get("status"));
        } catch (RestClientException e) { return null; }
    }

    @Override
    public void cancelar(String txid) {
        try { http.post().uri("/internal/simulate-cancel").contentType(MediaType.APPLICATION_JSON).body(Map.of("txid", txid)).retrieve().toBodilessEntity(); }
        catch (RestClientException ignorar) { /* já paga, cancelada ou inexistente */ }
    }

    @Override
    public void simularPagamento(String txid) {
        try { http.post().uri("/internal/simulate-pay").contentType(MediaType.APPLICATION_JSON).body(Map.of("txid", txid)).retrieve().toBodilessEntity(); }
        catch (RestClientException e) { throw new ProvedorIndisponivel(); }
    }

    static class ProvedorIndisponivel extends RuntimeException {
        ProvedorIndisponivel() { super("O Pix está fora do ar agora. Tente de novo em instantes ou pague com cartão."); }
    }
}
