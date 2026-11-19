package com.taskgo.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecuperacaoSenhaService {
    private static final Logger log=LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Duration VALIDADE=Duration.ofMinutes(30);
    private static final SecureRandom RANDOM=new SecureRandom();
    private final UsuarioRepository usuarios; private final TokenRecuperacaoSenhaRepository tokens;
    private final EmailRecuperacaoSenha email; private final RecuperacaoRateLimiter limiter;
    private final PasswordEncoder encoder; private final Clock clock;
    @Autowired
    public RecuperacaoSenhaService(UsuarioRepository usuarios,TokenRecuperacaoSenhaRepository tokens,EmailRecuperacaoSenha email,RecuperacaoRateLimiter limiter,PasswordEncoder encoder){this(usuarios,tokens,email,limiter,encoder,Clock.systemUTC());}
    RecuperacaoSenhaService(UsuarioRepository usuarios,TokenRecuperacaoSenhaRepository tokens,EmailRecuperacaoSenha email,RecuperacaoRateLimiter limiter,PasswordEncoder encoder,Clock clock){this.usuarios=usuarios;this.tokens=tokens;this.email=email;this.limiter=limiter;this.encoder=encoder;this.clock=clock;}

    private PoliticaSenha politicaSenha;
    @Autowired(required=false)
    void setPoliticaSenha(PoliticaSenha politicaSenha){this.politicaSenha=politicaSenha;}

    @Transactional
    public void solicitar(String endereco,String ip){
        if(endereco==null||endereco.length()>254||!EMAIL.matcher(endereco.trim()).matches())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um e-mail válido.");
        if(!limiter.permitir(ip==null?"unknown":ip))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Muitas solicitações. Aguarde e tente novamente.");
        if(!email.configurado())return;
        Usuario usuario=usuarios.findByEmailIgnoreCase(endereco.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if(usuario==null||usuario.getSenha()==null||usuario.getSenha().isBlank())return;
        /* Cada link enviado vale até vencer; usar qualquer um deles apaga os outros (em redefinir).
           Assim, quem pede de novo não fica com um e-mail antigo que já não funciona. */
        byte[] aleatorio=new byte[32];RANDOM.nextBytes(aleatorio);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        Instant agora=clock.instant();
        TokenRecuperacaoSenha novo=tokens.save(new TokenRecuperacaoSenha(UUID.randomUUID().toString(),usuario.getId(),hash(token),agora,agora.plus(VALIDADE)));
        try{email.enviarLink(usuario.getEmail(),token);}
        catch(RuntimeException falha){if(novo!=null)tokens.delete(novo);log.warn("Falha ao enviar mensagem de recuperação; canal SMTP indisponível ou erro do provedor.");}
    }

    /* Convite da equipe: link para criar a senha, válido por 48 horas. Usar o link também confirma o e-mail. */
    static final Duration VALIDADE_CONVITE=Duration.ofHours(48);
    @Transactional
    public void convidar(Usuario usuario,String quemConvidou){
        if(!email.configurado())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"O envio de e-mail não está configurado: não dá para mandar o convite.");
        byte[] aleatorio=new byte[32];RANDOM.nextBytes(aleatorio);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        Instant agora=clock.instant();
        TokenRecuperacaoSenha novo=tokens.save(new TokenRecuperacaoSenha(UUID.randomUUID().toString(),usuario.getId(),hash(token),agora,agora.plus(VALIDADE_CONVITE)));
        try{email.enviarConvite(usuario.getEmail(),usuario.getNome(),quemConvidou,token);}
        catch(RuntimeException falha){if(novo!=null)tokens.delete(novo);log.warn("Falha ao enviar convite da equipe.");throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível enviar o convite agora. Tente de novo.");}
    }

    @Transactional
    public void redefinir(String token,String novaSenha,String confirmarSenha){
        if(token==null||token.length()<32||token.length()>128||novaSenha==null||!novaSenha.equals(confirmarSenha))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Token inválido ou as senhas não conferem.");
        TokenRecuperacaoSenha recuperacao=tokens.findByHashForUpdate(hash(token)).orElseThrow(()->invalido());
        Instant agora=clock.instant();if(!recuperacao.validoEm(agora))throw invalido();
        Usuario usuario=usuarios.findById(recuperacao.getUsuarioId()).orElseThrow(()->invalido());
        if(politicaSenha!=null)politicaSenha.exigir(novaSenha,usuario.getEmail(),usuario.getNome());else PoliticaSenha.exigirLocal(novaSenha,usuario.getEmail(),usuario.getNome());
        usuario.setSenha(encoder.encode(novaSenha));usuario.setCredentialVersion(usuario.getCredentialVersion()+1);
        /* Quem abriu o link recebeu o e-mail: a conta fica confirmada e o convite, aceito. */
        usuario.getExtra().put(ConfirmacaoEmailService.CONFIRMADO,true);usuario.getExtra().remove("convitePendente");usuarios.save(usuario);
        recuperacao.marcarUsado(agora);tokens.save(recuperacao);tokens.deleteByUsuarioId(usuario.getId());
        try{email.enviarAvisoAlteracao(usuario.getEmail());}catch(RuntimeException falha){log.warn("Senha alterada, mas não foi possível enviar notificação de segurança.");}
    }
    private String hash(String token){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private ResponseStatusException invalido(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Link de recuperação inválido ou expirado. Solicite um novo link.");}
    public record SolicitarRequest(String email){}
    public record RedefinirRequest(String token,String novaSenha,String confirmarSenha){}
}
