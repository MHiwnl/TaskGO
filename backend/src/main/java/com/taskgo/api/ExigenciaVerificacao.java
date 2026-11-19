package com.taskgo.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verificação de identidade exigida só para usar os serviços:
 * o cliente para fazer pedidos e o profissional para receber oportunidades (mural, Chamar agora, aceitar, propostas).
 * O resto da conta (perfil, configurações, carteira, mensagens) funciona sem ela.
 */
@Component
public class ExigenciaVerificacao {
    private final UsuarioRepository usuarios;
    private final boolean ligada;

    public ExigenciaVerificacao(UsuarioRepository usuarios, @Value("${taskgo.verificacao.obrigatoria:true}") boolean ligada) {
        this.usuarios = usuarios; this.ligada = ligada;
    }

    public boolean ligada() { return ligada; }

    /** O profissional pode receber oportunidades e chamados (verificado, ou exigência desligada). */
    public boolean liberado(Usuario u) { return !ligada || VerificacaoController.verificado(u); }

    /** Cliente fazendo pedido. */
    public void exigirParaPedir(Long usuarioId) { exigir(usuarioId, "Verifique sua identidade para fazer pedidos."); }

    /** Profissional vendo, aceitando ou propondo em oportunidades. */
    public void exigirParaAtender(Long usuarioId) { exigir(usuarioId, "Verifique sua identidade para receber oportunidades."); }

    private void exigir(Long usuarioId, String mensagem) {
        if (!ligada) return;
        boolean ok = usuarios.findById(usuarioId).map(VerificacaoController::verificado).orElse(false);
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagem);
    }
}
