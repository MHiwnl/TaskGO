package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/* Números de um dia (fuso de Recife), calculados uma vez e guardados: os gráficos leem daqui. */
@Entity
@Table(name = "metricas_diarias")
public class MetricaDiaria {
    @Id private LocalDate dia;
    @Column(nullable = false) private long pedidos;
    @Column(nullable = false) private long concluidos;
    @Column(nullable = false) private long cancelados;
    @Column(name = "valor_concluido", nullable = false, precision = 19, scale = 2) private BigDecimal valorConcluido = BigDecimal.ZERO;
    @Column(name = "contas_novas", nullable = false) private long contasNovas;
    @Column(name = "clientes_novos", nullable = false) private long clientesNovos;
    @Column(name = "profissionais_novos", nullable = false) private long profissionaisNovos;
    @Column(nullable = false) private long propostas;
    @Column(name = "atualizado_em", nullable = false) private long atualizadoEm;

    protected MetricaDiaria() {}
    public MetricaDiaria(LocalDate dia) { this.dia = dia; }
    void preencher(long pedidos, long concluidos, long cancelados, BigDecimal valor, long clientes, long profissionais, long contas, long propostas, long agora) {
        this.pedidos = pedidos; this.concluidos = concluidos; this.cancelados = cancelados; this.valorConcluido = valor;
        this.clientesNovos = clientes; this.profissionaisNovos = profissionais; this.contasNovas = contas; this.propostas = propostas; this.atualizadoEm = agora;
    }
    public LocalDate getDia() { return dia; }
    public long getPedidos() { return pedidos; }
    public long getConcluidos() { return concluidos; }
    public long getCancelados() { return cancelados; }
    public BigDecimal getValorConcluido() { return valorConcluido; }
    public long getContasNovas() { return contasNovas; }
    public long getClientesNovos() { return clientesNovos; }
    public long getProfissionaisNovos() { return profissionaisNovos; }
    public long getPropostas() { return propostas; }
}
