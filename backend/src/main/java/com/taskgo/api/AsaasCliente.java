package com.taskgo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/* Cliente da API do Asaas (v3). Sandbox: https://api-sandbox.asaas.com/v3; produção: https://api.asaas.com/v3.
   A chave vai no cabeçalho access_token e nunca aparece em log nem em resposta. */
@Component
public class AsaasCliente {
    private final String url, chave;
    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();
    static final ZoneId FUSO = ZoneId.of("America/Recife");

    public AsaasCliente(@Value("${taskgo.pagamentos.asaas.url:https://api-sandbox.asaas.com/v3}") String url,
                        @Value("${taskgo.pagamentos.asaas.api-key:}") String chave) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
        this.chave = chave == null ? "" : chave.trim();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000); f.setReadTimeout(15000);
        this.http = RestClient.builder().baseUrl(this.url).requestFactory(f)
                .defaultHeader("access_token", this.chave).defaultHeader("User-Agent", "TaskGO").build();
    }

    public boolean configurado() { return !chave.isBlank(); }
    public boolean sandbox() { return url.contains("sandbox"); }

    /* Erro do Asaas com a mensagem que ele devolve (errors[0].description). */
    public static class Erro extends RuntimeException {
        final int status;
        Erro(int status, String msg) { super(msg); this.status = status; }
    }

    JsonNode get(String caminho) { return chamar("GET", caminho, null); }
    JsonNode post(String caminho, Object corpo) { return chamar("POST", caminho, corpo); }
    JsonNode delete(String caminho) { return chamar("DELETE", caminho, null); }

    private JsonNode chamar(String metodo, String caminho, Object corpo) {
        if (!configurado()) throw new Erro(503, "O pagamento não está configurado (falta a chave do Asaas).");
        try {
            RestClient.RequestBodySpec req = http.method(org.springframework.http.HttpMethod.valueOf(metodo)).uri(caminho);
            if (corpo != null) req.contentType(MediaType.APPLICATION_JSON).body(corpo);
            String texto = req.exchange((rq, rs) -> {
                String b = new String(rs.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                HttpStatusCode s = rs.getStatusCode();
                if (s.isError()) throw new Erro(s.value(), mensagem(b, s.value()));
                return b;
            });
            return texto == null || texto.isBlank() ? json.createObjectNode() : json.readTree(texto);
        } catch (Erro e) { throw e; }
        catch (RestClientException | java.io.IOException e) { throw new Erro(503, "O Asaas não respondeu. Tente de novo em instantes."); }
    }
    private String mensagem(String corpo, int status) {
        try {
            JsonNode e = json.readTree(corpo).path("errors");
            if (e.isArray() && e.size() > 0) return e.get(0).path("description").asText("Erro no pagamento.");
        } catch (Exception ignorar) { /* corpo sem JSON */ }
        return status == 401 ? "Chave do Asaas inválida." : "O Asaas recusou a operação (" + status + ").";
    }

    /* ---------- clientes ---------- */
    public String criarCliente(String nome, String cpf, String email, String telefone, String referencia) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", nome); c.put("cpfCnpj", cpf); c.put("email", email);
        if (telefone != null && !telefone.isBlank()) c.put("mobilePhone", telefone.replaceAll("\\D", ""));
        c.put("externalReference", referencia); c.put("notificationDisabled", true);
        return post("/customers", c).path("id").asText();
    }

    /* ---------- cobranças ---------- */
    public JsonNode criarCobranca(String cliente, String forma, BigDecimal valor, String descricao, String referencia) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("customer", cliente); c.put("billingType", forma); c.put("value", valor);
        c.put("dueDate", LocalDate.now(FUSO).plusDays(1).toString());
        c.put("description", descricao.length() > 500 ? descricao.substring(0, 500) : descricao);
        c.put("externalReference", referencia);
        return post("/payments", c);
    }
    /* Cobrança no cartão já tokenizado (cartão usado antes): não trafega número de cartão. */
    public JsonNode cobrarCartaoSalvo(String cliente, String token, BigDecimal valor, int parcelas, String descricao, String referencia, String ip) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("customer", cliente); c.put("billingType", "CREDIT_CARD"); c.put("creditCardToken", token);
        c.put("dueDate", LocalDate.now(FUSO).toString()); c.put("description", descricao); c.put("externalReference", referencia);
        c.put("remoteIp", ip == null || ip.isBlank() ? "127.0.0.1" : ip);
        if (parcelas > 1) { c.put("installmentCount", parcelas); c.put("totalValue", valor); } else c.put("value", valor);
        return post("/payments", c);
    }
    public JsonNode qrPix(String cobranca) { return get("/payments/" + cobranca + "/pixQrCode"); }
    public JsonNode cobranca(String cobranca) { return get("/payments/" + cobranca); }
    /* true só se a cobrança ficou cancelada no Asaas. Se o DELETE falhar, confere: pode ter sido paga no último instante. */
    public boolean cancelar(String cobranca) {
        try { delete("/payments/" + cobranca); return true; }
        catch (Erro e) {
            try { return "CANCELADA".equals(situacao(cobranca(cobranca).path("status").asText())); } catch (Erro semResposta) { return false; }
        }
    }
    /* Cobrança criada sem resposta (tempo esgotado): acha pelo nosso id, que vai em externalReference. */
    public JsonNode porReferencia(String referencia) { return get("/payments?externalReference=" + referencia); }
    /* Cartão em análise de risco ou ainda processando: não é recusa. */
    static boolean emAnalise(String statusAsaas) { return "AWAITING_RISK_ANALYSIS".equals(statusAsaas) || "PENDING".equals(statusAsaas); }
    public JsonNode estornar(String cobranca, BigDecimal valor, String motivo) {
        Map<String, Object> c = new LinkedHashMap<>(); c.put("value", valor); c.put("description", motivo);
        return post("/payments/" + cobranca + "/refund", c);
    }
    /* Só no sandbox: confirma o pagamento de uma cobrança. */
    public void confirmarNoSandbox(String cobranca) {
        if (!sandbox()) throw new Erro(403, "Só existe no ambiente de teste.");
        post("/sandbox/payment/" + cobranca + "/confirm", Map.of());
    }

    /* Situação traduzida: PAGA, PENDENTE ou CANCELADA. */
    static String situacao(String statusAsaas) {
        return switch (statusAsaas == null ? "" : statusAsaas) {
            case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> "PAGA";
            case "REFUNDED", "REFUND_REQUESTED", "DELETED", "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE" -> "CANCELADA";
            default -> "PENDENTE";
        };
    }

    /* ---------- transferências (resgate do profissional por chave Pix) ---------- */
    public JsonNode transferirPix(BigDecimal valor, String chavePix, String tipoChave, String descricao) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("value", valor); c.put("operationType", "PIX"); c.put("pixAddressKey", chavePix); c.put("pixAddressKeyType", tipoChave); c.put("description", descricao);
        return post("/transfers", c);
    }
    public JsonNode transferencia(String id) { return get("/transfers/" + id); }
    public JsonNode saldo() { return get("/finance/balance"); }
}
