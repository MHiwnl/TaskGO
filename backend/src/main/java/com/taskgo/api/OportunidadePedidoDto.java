package com.taskgo.api;

/* O que o profissional vê antes do aceite: sem endereço nem contato; com o primeiro nome e a nota do cliente. */
public record OportunidadePedidoDto(String id, String service, String notes, Long created,
                                    String status, Double price, Long sched, boolean urgente,
                                    String clienteNome, Double clienteNota, int clienteAvaliacoes, String bairro,
                                    Long clienteUid, boolean clienteTemFoto, boolean agora, Double km, java.util.List<String> fotos, String conflito, boolean obra, java.util.Map<String, Object> recorrente) {
    /* Distância do profissional até o pedido, em km com uma casa (o endereço continua escondido). */
    public OportunidadePedidoDto comKm(Double distancia) {
        return new OportunidadePedidoDto(id, service, notes, created, status, price, sched, urgente, clienteNome, clienteNota, clienteAvaliacoes,
                bairro, clienteUid, clienteTemFoto, agora, distancia == null ? null : Math.round(distancia * 10) / 10.0, fotos, conflito, obra, recorrente);
    }
    public OportunidadePedidoDto comFotos(java.util.List<String> ids) {
        return new OportunidadePedidoDto(id, service, notes, created, status, price, sched, urgente, clienteNome, clienteNota, clienteAvaliacoes,
                bairro, clienteUid, clienteTemFoto, agora, km, ids == null ? java.util.List.of() : ids, conflito, obra, recorrente);
    }
    /* Motivo de choque com a agenda do profissional (pedido com data marcada). */
    public OportunidadePedidoDto comConflito(String motivo) {
        return new OportunidadePedidoDto(id, service, notes, created, status, price, sched, urgente, clienteNome, clienteNota, clienteAvaliacoes,
                bairro, clienteUid, clienteTemFoto, agora, km, fotos, motivo, obra, recorrente);
    }
    public static OportunidadePedidoDto from(Pedido pedido) { return from(pedido, null, null, 0, null, false); }
    public static OportunidadePedidoDto from(Pedido pedido, String clienteNome, Double clienteNota, int clienteAvaliacoes) { return from(pedido, clienteNome, clienteNota, clienteAvaliacoes, null, false); }
    @SuppressWarnings("unchecked")
    public static OportunidadePedidoDto from(Pedido pedido, String clienteNome, Double clienteNota, int clienteAvaliacoes, Long clienteUid, boolean clienteTemFoto) {
        String notes = pedido.getNotes();
        if (notes != null) {
            notes = notes.replaceAll("(?i)[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}", "[contato removido]")
                    .replaceAll("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[- .]?\\d{4}(?!\\d)", "[telefone removido]");
        }
        Object bairro = pedido.getExtra().get("bairro");
        return new OportunidadePedidoDto(pedido.getId(), pedido.getService(), notes,
                pedido.getCreated(), pedido.getStatus(), pedido.getPrice(), pedido.getSched(),
                Boolean.TRUE.equals(pedido.getExtra().get("urgente")), clienteNome, clienteNota, clienteAvaliacoes,
                bairro instanceof String b && !b.isBlank() ? b : null, clienteUid, clienteTemFoto, ChamarAgoraService.modoAgora(pedido), null, java.util.List.of(), null, ObraService.eh(pedido),
                pedido.getExtra().get("recorrente") instanceof java.util.Map<?, ?> r ? new java.util.LinkedHashMap<>((java.util.Map<String, Object>) r) : null);
    }
}
