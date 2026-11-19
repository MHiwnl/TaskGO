package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* Orçamento de uma obra: etapas (JSON), material, início, dias e horário de trabalho e previsão de término. */
@Entity
@Table(name = "orcamentos")
public class Orcamento {
    public static final String ENVIADO = "enviado", ACEITO = "aceito", RECUSADO = "recusado", RETIRADO = "retirado";

    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "profissional_id", nullable = false) private Long profissionalId;
    @Column(name = "profissional_email", nullable = false) private String profissionalEmail;
    @Column(name = "profissional_nome") private String profissionalNome;
    @Column(nullable = false, columnDefinition = "text") private String etapas;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal total;
    @Column(nullable = false) private String material;
    @Column(name = "lista_material", columnDefinition = "text") private String listaMaterial;
    @Column(nullable = false) private long inicio;
    @Column(name = "dias_trabalho", nullable = false) private String diasTrabalho;
    @Column(nullable = false) private String horario;
    @Column(name = "previsao_fim", nullable = false) private long previsaoFim;
    @Column(columnDefinition = "text") private String observacao;
    @Column(nullable = false) private String status = ENVIADO;
    @Column(name = "contrato_pro_em") private Long contratoProEm;
    @Column(name = "contrato_cliente_em") private Long contratoClienteEm;
    @Column(name = "criado_em", nullable = false) private long criadoEm;
    @Column(name = "respondido_em") private Long respondidoEm;

    protected Orcamento() {}
    public Orcamento(String id, String pedidoId, Usuario pro, String etapas, BigDecimal total, String material, String listaMaterial,
                     long inicio, String diasTrabalho, String horario, long previsaoFim, String observacao, Long contratoProEm, long criadoEm) {
        this.id = id; this.pedidoId = pedidoId; this.profissionalId = pro.getId(); this.profissionalEmail = pro.getEmail(); this.profissionalNome = pro.getNome();
        this.etapas = etapas; this.total = total; this.material = material; this.listaMaterial = listaMaterial; this.inicio = inicio;
        this.diasTrabalho = diasTrabalho; this.horario = horario; this.previsaoFim = previsaoFim; this.observacao = observacao;
        this.contratoProEm = contratoProEm; this.criadoEm = criadoEm;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getProfissionalId() { return profissionalId; }
    public String getProfissionalEmail() { return profissionalEmail; }
    public String getProfissionalNome() { return profissionalNome; }
    public String getEtapas() { return etapas; }
    public BigDecimal getTotal() { return total; }
    public String getMaterial() { return material; }
    public String getListaMaterial() { return listaMaterial; }
    public long getInicio() { return inicio; }
    public String getDiasTrabalho() { return diasTrabalho; }
    public String getHorario() { return horario; }
    public long getPrevisaoFim() { return previsaoFim; }
    public String getObservacao() { return observacao; }
    public String getStatus() { return status; }
    public Long getContratoProEm() { return contratoProEm; }
    public Long getContratoClienteEm() { return contratoClienteEm; }
    public long getCriadoEm() { return criadoEm; }
    public Long getRespondidoEm() { return respondidoEm; }
    void responder(String status, long quando) { this.status = status; this.respondidoEm = quando; }
    void contratoCliente(long quando) { this.contratoClienteEm = quando; }
    void novaPrevisao(long fim) { this.previsaoFim = fim; }
    void novoTotal(BigDecimal t) { this.total = t; }
}
