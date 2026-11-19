package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(name="solicitacoes_saque", uniqueConstraints=@UniqueConstraint(name="uk_saque_idempotencia", columnNames={"usuario_id","chave_idempotencia"}))
public class SolicitacaoSaque {
    @Id private String id;
    @Column(name="usuario_id",nullable=false) private Long usuarioId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal valor;
    @Column(nullable=false,length=3) private String moeda="BRL";
    @Column(nullable=false,length=30) private String status;
    @Column(name="chave_idempotencia",nullable=false,length=100) private String chaveIdempotencia;
    @Column(name="criada_em",nullable=false) private long criadaEm;
    @Column(name="transferencia_id") private String transferenciaId;
    @Column(name="chave_pix") private String chavePix;

    protected SolicitacaoSaque() {}
    public SolicitacaoSaque(String id,Long usuarioId,BigDecimal valor,String chaveIdempotencia,long criadaEm){this.id=id;this.usuarioId=usuarioId;this.valor=valor;this.chaveIdempotencia=chaveIdempotencia;this.criadaEm=criadaEm;this.status="SOLICITADO";}
    public String getId(){return id;} public Long getUsuarioId(){return usuarioId;} public BigDecimal getValor(){return valor;}
    public String getMoeda(){return moeda;} public String getStatus(){return status;} public String getChaveIdempotencia(){return chaveIdempotencia;} public long getCriadaEm(){return criadaEm;}
    public String getTransferenciaId(){return transferenciaId;} public String getChavePix(){return chavePix;}
    void enviada(String transferenciaId,String chavePix){this.transferenciaId=transferenciaId;this.chavePix=chavePix;this.status="PROCESSANDO";}
    void situacao(String status){this.status=status;}
}
