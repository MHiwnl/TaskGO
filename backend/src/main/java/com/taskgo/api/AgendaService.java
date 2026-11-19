package com.taskgo.api;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* Agenda do profissional: atendimentos com data marcada (contam 2 horas) e horários bloqueados.
   Aceitar um pedido agendado que bate com outro atendimento ou com um bloqueio é recusado. */
@Service
public class AgendaService {
    static final long DURACAO_MS = 2 * 60 * 60 * 1000L, MAX_BLOQUEIO_MS = 14L * 24 * 60 * 60 * 1000;
    static final Set<String> ATIVOS = Set.of("a_caminho", "chegou", "em_andamento");
    static final ZoneId FUSO = ZoneId.of("America/Recife");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR")).withZone(FUSO);

    private final BloqueioAgendaRepository bloqueios;
    private final PedidoContratadoRepository pedidos;
    public AgendaService(BloqueioAgendaRepository bloqueios, PedidoContratadoRepository pedidos) { this.bloqueios = bloqueios; this.pedidos = pedidos; }

    /* Atendimentos com data marcada que o profissional já assumiu. */
    @Transactional(readOnly = true)
    public List<Pedido> agendados(String proEmail) {
        if (proEmail == null) return List.of();
        return pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(proEmail, proEmail).stream()
                .filter(p -> proEmail.equalsIgnoreCase(String.valueOf(p.getProEmail())) && p.getSched() != null && ATIVOS.contains(p.getStatus())).toList();
    }

    /* Motivo do conflito para atender em [inicio, inicio + 2 h), ou null se o horário está livre. */
    @Transactional(readOnly = true)
    public String conflito(Long proId, String proEmail, long inicio, String ignorarPedido) {
        long fim = inicio + DURACAO_MS;
        for (BloqueioAgenda b : bloqueios.entre(proId, inicio, fim))
            return "Você bloqueou esse horário na agenda" + (b.getMotivo() == null || b.getMotivo().isBlank() ? "" : " (" + b.getMotivo() + ")") + ".";
        for (Pedido p : agendados(proEmail))
            if (!p.getId().equals(ignorarPedido) && p.getSched() < fim && p.getSched() + DURACAO_MS > inicio)
                return "Você já tem um atendimento em " + HORA.format(Instant.ofEpochMilli(p.getSched())) + ".";
        return null;
    }

    /* Bloqueio valendo agora (para o Chamar Agora não chamar quem está de folga). */
    @Transactional(readOnly = true)
    public boolean bloqueadoAgora(Long proId) {
        long agora = System.currentTimeMillis();
        return !bloqueios.entre(proId, agora, agora + 1).isEmpty();
    }

    public void exigirLivre(Usuario pro, Pedido p) {
        if (p.getSched() == null) return;
        String motivo = conflito(pro.getId(), pro.getEmail(), p.getSched(), p.getId());
        if (motivo != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido bate com a sua agenda. " + motivo);
    }

    @Transactional
    public BloqueioAgenda bloquear(Usuario pro, long inicio, long fim, String motivo) {
        long agora = System.currentTimeMillis();
        if (fim <= inicio) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim precisa ser depois do início.");
        if (fim <= agora) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esse horário já passou.");
        if (fim - inicio > MAX_BLOQUEIO_MS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bloqueie no máximo 14 dias de uma vez.");
        if (bloqueios.countByUsuarioIdAndFimGreaterThan(pro.getId(), agora) >= 200) throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já tem 200 bloqueios futuros. Apague alguns antes.");
        for (Pedido p : agendados(pro.getEmail()))
            if (p.getSched() < fim && p.getSched() + DURACAO_MS > inicio)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Você tem um atendimento em " + HORA.format(Instant.ofEpochMilli(p.getSched())) + ". Não dá para bloquear esse horário.");
        String m = motivo == null ? null : motivo.trim();
        if (m != null && m.length() > 80) m = m.substring(0, 80);
        return bloqueios.save(new BloqueioAgenda(UUID.randomUUID().toString(), pro.getId(), inicio, fim, m == null || m.isBlank() ? null : m, agora));
    }

    @Transactional
    public void desbloquear(Long proId, String id) {
        bloqueios.delete(bloqueios.findByIdAndUsuarioId(id, proId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bloqueio não encontrado.")));
    }

    @Transactional(readOnly = true)
    public List<BloqueioAgenda> bloqueiosEntre(Long proId, long de, long ate) { return bloqueios.entre(proId, de, ate); }
}
