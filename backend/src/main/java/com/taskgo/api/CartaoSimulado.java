package com.taskgo.api;

import java.util.Map;

/* Cartão simulado: valida o número (Luhn), descobre a bandeira e decide o resultado da cobrança pelo número de teste.
   Nenhum número completo sai daqui: quem chama guarda só o token, a bandeira e o final. */
final class CartaoSimulado {
    private CartaoSimulado() {}

    static final String APROVADO = "aprovado";
    /* Números de teste com resultado fixo; qualquer outro número válido é aprovado. */
    static final Map<String, String> TESTES = Map.of(
            "4000000000000002", "recusado",
            "4000000000009995", "sem_saldo",
            "4000000000000069", "bloqueado");
    static final Map<String, String> MOTIVOS = Map.of(
            "recusado", "O banco recusou o pagamento. Tente outro cartão ou pague com Pix.",
            "sem_saldo", "Saldo ou limite insuficiente neste cartão.",
            "bloqueado", "Este cartão está bloqueado pelo banco.");

    static String somenteDigitos(String numero) { return numero == null ? "" : numero.replaceAll("\\D", ""); }

    static boolean luhn(String digitos) {
        if (digitos.length() < 13 || digitos.length() > 19) return false;
        int soma = 0; boolean dobra = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (dobra) { d *= 2; if (d > 9) d -= 9; }
            soma += d; dobra = !dobra;
        }
        return soma % 10 == 0;
    }

    static String bandeira(String d) {
        if (d.matches("^(4011|4312|4389|4514|4576|5041|5066|5067|509\\d|6277|6362|6363|650\\d|6516|6550).*")) return "Elo";
        if (d.matches("^(606282|3841).*")) return "Hipercard";
        if (d.matches("^3[47].*")) return "Amex";
        if (d.matches("^4.*")) return "Visa";
        if (d.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720).*")) return "Mastercard";
        return null;
    }

    static String resultado(String digitos) { return TESTES.getOrDefault(digitos, APROVADO); }

    record Resposta(boolean aprovado, String motivo) {}
    static Resposta cobrar(Cartao c) {
        String r = c.getResultadoSimulado() == null ? APROVADO : c.getResultadoSimulado();
        return APROVADO.equals(r) ? new Resposta(true, null) : new Resposta(false, MOTIVOS.getOrDefault(r, MOTIVOS.get("recusado")));
    }
}
