package com.taskgo.api;

/* pro: Plano Pro valendo agora (selo e destaque na lista). */
public record ProfissionalPublicoDto(String id, String name, String service, String city, String bio, Double rating, long reviews, boolean verificado, boolean pro) {
    static ProfissionalPublicoDto from(Usuario u, Double rating, long reviews, boolean pro) {
        return new ProfissionalPublicoDto(u.getProId(), u.getNome(), u.getService(), u.getCidade(), u.getBio(), rating, reviews, VerificacaoController.verificado(u), pro);
    }
}
