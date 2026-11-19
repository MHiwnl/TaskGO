package com.taskgo.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/* Põe um pedido para procurar profissional: Chamar Agora vai em ondas para os mais perto;
   Agendar aparece no mural da categoria e avisa todos dela que estão online.
   Roda ao enviar (pagamento desligado) ou quando o pagamento é confirmado. */
@Service
public class PublicacaoPedidoService {
    private final UsuarioRepository usuarios;
    private final EventosService eventos;
    private ChamarAgoraService agora;
    private PresencaProfissionalRepository presencas;
    private AgendaService agenda;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setAgendaService(AgendaService agenda) { this.agenda = agenda; }
    private ExigenciaVerificacao exigencia;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }
    @Autowired(required = false) void setPresencas(PresencaProfissionalRepository presencas) { this.presencas = presencas; }
    private NotificacaoService notificacoes;

    public PublicacaoPedidoService(UsuarioRepository usuarios, EventosService eventos) { this.usuarios = usuarios; this.eventos = eventos; }
    @Autowired(required = false) void setAgora(ChamarAgoraService agora) { this.agora = agora; }
    @Autowired(required = false) void setNotificacoes(NotificacaoService notificacoes) { this.notificacoes = notificacoes; }

    public void publicar(Pedido p) {
        if (p.getProEmail() != null) {
            /* Contratação direta: só o profissional escolhido fica sabendo. */
            usuarios.findByEmailIgnoreCase(p.getProEmail()).ifPresent(u -> {
                eventos.enviar(u.getId(), "pedido", p.getId());
                avisar(u.getId(), p.getId(), "Você foi contratado", "Um cliente contratou você para " + PedidoContratadoController.nomeServico(p.getService()).toLowerCase(java.util.Locale.ROOT) + valor(p) + ".", "contratado:" + p.getId());
            });
            return;
        }
        if (ChamarAgoraService.modoAgora(p) && agora != null) { agora.iniciar(p); return; }
        usuarios.profissionaisDoServico(p.getService()).stream()
                .filter(DisponibilidadeController::online)
                .filter(u -> exigencia == null || exigencia.liberado(u))
                .filter(u -> AreaAtendimentoController.dentro(u, AreaAtendimentoController.ponto(u, presencas), p))
                .filter(u -> agenda == null || p.getSched() == null || agenda.conflito(u.getId(), u.getEmail(), p.getSched(), p.getId()) == null).forEach(u -> {
                    eventos.enviar(u.getId(), "oportunidade", p.getId());
                    avisar(u.getId(), p.getId(), "Novo pedido de " + PedidoContratadoController.nomeServico(p.getService()).toLowerCase(java.util.Locale.ROOT),
                            "Um cliente" + (p.getExtra().get("bairro") instanceof String b && !b.isBlank() ? " em " + b : "") + " pediu" + valor(p) + ". Veja em Oportunidades.", "oportunidade:" + p.getId());
                });
    }

    private static String valor(Pedido p) {
        return p.getPrice() == null ? "" : String.format(java.util.Locale.forLanguageTag("pt-BR"), " · R$ %.2f", p.getPrice());
    }
    private void avisar(Long usuarioId, String pedidoId, String titulo, String texto, String chave) {
        if (notificacoes == null || usuarioId == null) return;
        try { notificacoes.criar(usuarioId, pedidoId, "pedido", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
}
