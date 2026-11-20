package com.taskgo.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/*
 * Ranking por qualidade: nota das avaliações (com prior para quem é novo), conclusão (concluídos x cancelamentos
 * feitos pelo próprio profissional) e rapidez de resposta. As funções são puras, em 0..1; só as contagens vêm do banco.
 */
@Service
public class RankingProfissionais {
    static final long DIA_MS = 86_400_000L, JANELA_MS = 180 * DIA_MS;
    /* Resposta acima de 24 h conta como 24 h. */
    static final double TETO_RESPOSTA_MIN = 24 * 60;

    /* mediaRespostaMin e media ficam null sem dados; nota e conclusao já vêm em 0..1. */
    public record Metricas(long concluidos, long cancelamentos, Double mediaRespostaMin, double nota, long avaliacoes, Double media) {}

    private final PedidoContratadoRepository pedidos;
    private final HistoricoContaRepository historico;

    public RankingProfissionais(PedidoContratadoRepository pedidos, HistoricoContaRepository historico) {
        this.pedidos = pedidos; this.historico = historico;
    }

    /* Nota bayesiana: sem avaliações vale 4.5 de 5; cada avaliação puxa a média para o lugar dela. */
    public static double nota(double media, long avaliacoes) { return (media * avaliacoes + 4.5 * 5) / (avaliacoes + 5) / 5; }

    /* Prior de 90% com 3 pedidos: quem cancela desce. */
    public static double conclusao(long concluidos, long cancelamentos) { return (concluidos + 0.9 * 3) / (concluidos + cancelamentos + 3); }

    /* Sem dados vale 0.5; 30 min vale 0.5; 0 min vale 1. */
    public static double resposta(Double minutos) {
        if (minutos == null) return 0.5;
        return 1 / (1 + Math.min(Math.max(minutos, 0), TETO_RESPOSTA_MIN) / 30);
    }

    public static double distancia(double km, double raio) { return Math.max(0, 1 - km / raio); }

    public static double pontuacaoLista(Metricas m) {
        return 0.45 * m.nota() + 0.35 * conclusao(m.concluidos(), m.cancelamentos()) + 0.20 * resposta(m.mediaRespostaMin());
    }

    public static double pontuacaoOnda(double km, double raio, Metricas m) {
        return 0.5 * distancia(km, raio) + 0.25 * m.nota() + 0.15 * conclusao(m.concluidos(), m.cancelamentos()) + 0.10 * resposta(m.mediaRespostaMin());
    }

    /* Uma entrada por profissional (chave: id). Duas consultas agrupadas de pedidos e uma do histórico, na janela de 180 dias. */
    public Map<Long, Metricas> metricas(Collection<Usuario> profs) {
        Map<Long, Metricas> r = new HashMap<>();
        if (profs == null || profs.isEmpty()) return r;
        long desde = System.currentTimeMillis() - JANELA_MS;
        List<String> emails = profs.stream().map(RankingProfissionais::chave).filter(Objects::nonNull).distinct().toList();
        List<Long> ids = profs.stream().map(Usuario::getId).filter(Objects::nonNull).toList();
        Map<String, Object[]> pedidosDe = new HashMap<>(), notasDe = new HashMap<>();
        Map<Long, Long> cancelamentos = new HashMap<>();
        if (!emails.isEmpty()) {
            for (Object[] l : pedidos.metricasDe(emails, desde)) pedidosDe.put(String.valueOf(l[0]), l);
            for (Object[] l : pedidos.resumoAvaliacoesDe(emails)) notasDe.put(String.valueOf(l[0]), l);
        }
        if (!ids.isEmpty()) for (Object[] l : historico.cancelamentosDe(ids, desde)) cancelamentos.put(((Number) l[0]).longValue(), ((Number) l[1]).longValue());
        for (Usuario p : profs) {
            if (p.getId() == null) continue;
            String chave = chave(p);
            Object[] pe = chave == null ? null : pedidosDe.get(chave), nt = chave == null ? null : notasDe.get(chave);
            long concluidos = pe == null ? 0 : ((Number) pe[1]).longValue();
            Double ms = pe == null ? null : numero(pe[2]);
            Double minutos = ms == null ? null : ms / 60_000;
            Double media = nt == null ? null : numero(nt[1]);
            long avaliacoes = nt == null ? 0 : ((Number) nt[2]).longValue();
            double nota = nota(media == null ? 0 : media, avaliacoes);
            r.put(p.getId(), new Metricas(concluidos, cancelamentos.getOrDefault(p.getId(), 0L), minutos, nota, avaliacoes, media));
        }
        return r;
    }

    static String chave(Usuario p) { return p.getEmail() == null ? null : p.getEmail().toLowerCase(Locale.ROOT); }

    private static Double numero(Object o) { return o instanceof Number n ? n.doubleValue() : null; }
}
