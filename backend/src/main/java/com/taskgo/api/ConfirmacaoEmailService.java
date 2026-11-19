package com.taskgo.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/* Confirmação do e-mail no cadastro: um código de 6 números vai para o e-mail e a conta só entra depois de digitá-lo.
   Assim ninguém cria conta com um e-mail que não é seu, inclusive o do administrador.
   Contas antigas (sem a marca) contam como confirmadas. Sem e-mail configurado, a conta é confirmada na hora. */
@Service
public class ConfirmacaoEmailService {
    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoEmailService.class);
    static final String CONFIRMADO = "emailConfirmado", HASH = "emailCodigoHash", EXPIRA = "emailCodigoExpira",
            TENTATIVAS = "emailCodigoTentativas", ENVIADO = "emailCodigoEnviadoEm";
    static final long VALIDADE_MS = 15 * 60_000L, ESPERA_REENVIO_MS = 60_000L;
    static final int MAX_TENTATIVAS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UsuarioRepository usuarios;
    private final EmailRecuperacaoSenha email;

    public ConfirmacaoEmailService(UsuarioRepository usuarios, EmailRecuperacaoSenha email) { this.usuarios = usuarios; this.email = email; }

    public static boolean confirmado(Usuario u) { return !Boolean.FALSE.equals(u.getExtra().get(CONFIRMADO)); }

    /* Conta nova: marca como não confirmada e manda o código. Devolve se a confirmação é exigida. */
    public boolean iniciar(Usuario u) {
        if (!email.canalPronto()) { u.getExtra().put(CONFIRMADO, true); log.warn("E-mail não configurado: conta {} confirmada sem código.", u.getId()); return false; }
        u.getExtra().put(CONFIRMADO, false);
        enviarCodigo(u);
        return true;
    }

    public void reenviar(String endereco) {
        Usuario u = usuarios.findByEmailIgnoreCase(normalizar(endereco)).orElse(null);
        if (u == null || confirmado(u)) return; /* não revela se a conta existe */
        Object ultimo = u.getExtra().get(ENVIADO);
        if (ultimo instanceof Number n && System.currentTimeMillis() - n.longValue() < ESPERA_REENVIO_MS)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Espere um minuto para pedir outro código.");
        enviarCodigo(u);
        usuarios.save(u);
    }

    /* Confere o código; certo, a conta fica confirmada e é devolvida para abrir a sessão. */
    public Usuario confirmar(String endereco, String codigo) {
        Usuario u = usuarios.findByEmailIgnoreCase(normalizar(endereco)).orElseThrow(this::invalido);
        if (confirmado(u)) return u;
        int tentativas = u.getExtra().get(TENTATIVAS) instanceof Number n ? n.intValue() : 0;
        long expira = u.getExtra().get(EXPIRA) instanceof Number n ? n.longValue() : 0L;
        if (tentativas >= MAX_TENTATIVAS) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas com este código. Peça um código novo.");
        if (System.currentTimeMillis() > expira) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O código venceu. Peça um código novo.");
        String limpo = codigo == null ? "" : codigo.replaceAll("[^0-9]", "");
        if (!MessageDigest.isEqual(hash(limpo).getBytes(StandardCharsets.UTF_8), String.valueOf(u.getExtra().get(HASH)).getBytes(StandardCharsets.UTF_8))) {
            u.getExtra().put(TENTATIVAS, tentativas + 1);
            usuarios.save(u);
            int restam = MAX_TENTATIVAS - tentativas - 1;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, restam > 0 ? "Código incorreto. Restam " + restam + (restam == 1 ? " tentativa." : " tentativas.") : "Código incorreto. Peça um código novo.");
        }
        u.getExtra().put(CONFIRMADO, true);
        for (String k : new String[]{HASH, EXPIRA, TENTATIVAS, ENVIADO}) u.getExtra().remove(k);
        return usuarios.save(u);
    }

    private void enviarCodigo(Usuario u) {
        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        long agora = System.currentTimeMillis();
        u.getExtra().put(HASH, hash(codigo));
        u.getExtra().put(EXPIRA, agora + VALIDADE_MS);
        u.getExtra().put(TENTATIVAS, 0);
        u.getExtra().put(ENVIADO, agora);
        try {
            email.enviarTexto(u.getEmail(), "Seu código TaskGO: " + codigo,
                    "Olá" + (u.getNome() == null ? "" : ", " + u.getNome().trim().split(" +")[0]) + ".\n\nSeu código para confirmar o e-mail na TaskGO é:\n\n" + codigo
                            + "\n\nEle vale por 15 minutos. Se você não criou uma conta na TaskGO, ignore esta mensagem.");
        } catch (RuntimeException falha) {
            log.warn("Falha ao enviar o código de confirmação de e-mail.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível enviar o código agora. Tente de novo em alguns minutos.");
        }
    }
    private ResponseStatusException invalido() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto ou conta não encontrada."); }
    private static String normalizar(String e) { return e == null ? "" : e.trim().toLowerCase(Locale.ROOT); }
    private static String hash(String v) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
