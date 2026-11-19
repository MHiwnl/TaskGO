package com.taskgo.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Resumo diário: cada dia fechado é calculado uma vez (à noite) com contagens por intervalo no banco e guardado.
   O dia de hoje é calculado na hora, com as mesmas consultas. Abrir um ano de gráfico custa o mesmo que abrir uma semana. */
@Service
public class MetricasService {
    static final ZoneId FUSO = ZoneId.of("America/Recife");
    private final MetricaDiariaRepository metricas;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final PropostaRepository propostas;
    private final HistoricoContaRepository historico;

    public MetricasService(MetricaDiariaRepository metricas, PedidoContratadoRepository pedidos, UsuarioRepository usuarios,
                           PropostaRepository propostas, HistoricoContaRepository historico) {
        this.metricas = metricas; this.pedidos = pedidos; this.usuarios = usuarios; this.propostas = propostas; this.historico = historico;
    }

    static long inicio(LocalDate d) { return d.atStartOfDay(FUSO).toInstant().toEpochMilli(); }

    /* Os números de um intervalo [a, b), direto do banco. */
    public record Numeros(long pedidos, long concluidos, long cancelados, BigDecimal valor, long clientes, long profissionais, long contas, long propostas) {}
    @Transactional(readOnly = true)
    public Numeros calcular(long a, long b) {
        Object[] c = pedidos.concluidosEntre(a, b).get(0);
        long clientes = 0, pros = 0, contas = 0;
        for (Object[] l : usuarios.novosEntre(a, b)) {
            String tipo = String.valueOf(l[0]); long n = ((Number) l[1]).longValue();
            if (PapelEquipe.equipe(tipo)) continue;
            contas += n; if ("cliente".equals(tipo)) clientes += n; else if ("profissional".equals(tipo)) pros += n;
        }
        BigDecimal valor = c[1] == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(c[1])).setScale(2, RoundingMode.HALF_UP);
        return new Numeros(pedidos.criadosEntre(a, b), ((Number) c[0]).longValue(), historico.contarTipoEntre("cancelamento", a, b),
                valor, clientes, pros, contas, propostas.criadasEntre(a, b));
    }

    @Transactional
    public MetricaDiaria fecharDia(LocalDate dia) {
        Numeros n = calcular(inicio(dia), inicio(dia.plusDays(1)));
        MetricaDiaria m = metricas.findById(dia).orElseGet(() -> new MetricaDiaria(dia));
        m.preencher(n.pedidos(), n.concluidos(), n.cancelados(), n.valor(), n.clientes(), n.profissionais(), n.contas(), n.propostas(), System.currentTimeMillis());
        return metricas.save(m);
    }

    /* À noite (00:20 de Recife) fecha ontem e preenche dias que faltarem nos últimos 90. Também roda ao subir o servidor. */
    @Scheduled(cron = "0 20 0 * * *", zone = "America/Recife")
    public void fecharDiasPendentes() {
        LocalDate hoje = LocalDate.now(FUSO);
        for (LocalDate d = hoje.minusDays(90); d.isBefore(hoje); d = d.plusDays(1))
            if (d.equals(hoje.minusDays(1)) || !metricas.existsById(d)) fecharDia(d);
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void aoSubir() {
        Thread t = new Thread(() -> { try { fecharDiasPendentes(); } catch (RuntimeException ignorar) { /* tenta de novo à noite */ } }, "metricas-inicio");
        t.setDaemon(true); t.start();
    }

    /* Série de dias para os gráficos: dias fechados da tabela + hoje ao vivo. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> serie(int dias) {
        LocalDate hoje = LocalDate.now(FUSO), de = hoje.minusDays(dias - 1L);
        Map<LocalDate, MetricaDiaria> guardados = new java.util.HashMap<>();
        metricas.findByDiaBetweenOrderByDiaAsc(de, hoje.minusDays(1)).forEach(m -> guardados.put(m.getDia(), m));
        List<Map<String, Object>> r = new ArrayList<>();
        for (LocalDate d = de; !d.isAfter(hoje); d = d.plusDays(1)) {
            Numeros n;
            if (d.equals(hoje)) n = calcular(inicio(d), System.currentTimeMillis() + 1);
            else {
                MetricaDiaria m = guardados.get(d);
                n = m == null ? calcular(inicio(d), inicio(d.plusDays(1)))
                        : new Numeros(m.getPedidos(), m.getConcluidos(), m.getCancelados(), m.getValorConcluido(), m.getClientesNovos(), m.getProfissionaisNovos(), m.getContasNovas(), m.getPropostas());
            }
            r.add(AdminController.mapa("dia", d.toString(), "pedidos", n.pedidos(), "concluidos", n.concluidos(), "cancelados", n.cancelados(),
                    "valor", n.valor(), "novosClientes", n.clientes(), "novosProfissionais", n.profissionais(), "contasNovas", n.contas(), "propostas", n.propostas(),
                    "parcial", d.equals(hoje)));
        }
        return r;
    }
}
