package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Chave gerada pelo próprio sistema e guardada no banco (por exemplo, o par VAPID do push). */
@Entity
@Table(name = "chaves_sistema")
public class ChaveSistema {
    @Id private String nome;
    @Column(nullable = false, columnDefinition = "text") private String valor;
    protected ChaveSistema() {}
    public ChaveSistema(String nome, String valor) { this.nome = nome; this.valor = valor; }
    public String getNome() { return nome; }
    public String getValor() { return valor; }
}
