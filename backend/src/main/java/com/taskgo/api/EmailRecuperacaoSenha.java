package com.taskgo.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EmailRecuperacaoSenha {
    private final JavaMailSender sender;
    private final String host,from,frontendUrl;
    public EmailRecuperacaoSenha(ObjectProvider<JavaMailSender> senders,
            @Value("${spring.mail.host:}") String host,
            @Value("${taskgo.mail.from:}") String from,
            @Value("${taskgo.auth.password-reset.frontend-url:}") String frontendUrl){this.sender=senders.getIfAvailable();this.host=host;this.from=from;this.frontendUrl=frontendUrl;}
    public boolean configurado(){
        if(sender==null||host.isBlank()||from.isBlank())return false;
        try{
            java.net.URI uri=java.net.URI.create(frontendUrl);
            boolean secure="https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null;
            boolean localHttp="http".equalsIgnoreCase(uri.getScheme())&&java.util.Set.of("localhost","127.0.0.1","::1").contains(uri.getHost());
            return (secure||localHttp)&&uri.getUserInfo()==null&&uri.getFragment()!=null;
        }catch(RuntimeException e){return false;}
    }
    public void enviarLink(String destino,String token){
        if(!configurado())throw new IllegalStateException("Canal de recuperação não configurado.");
        String separador=frontendUrl.contains("?")?"&":"?";
        String link=frontendUrl+separador+"token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
        SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(destino);mail.setSubject("Redefinição de senha TaskGO");
        mail.setText("Recebemos uma solicitação para redefinir sua senha TaskGO. Use o link abaixo em até 30 minutos. Se não foi você, ignore esta mensagem.\n\n"+link+"\n\nPor segurança, não compartilhe este link.");
        sender.send(mail);
    }
    /* Canal de e-mail pronto para mensagens sem link (código de confirmação, avisos). */
    public boolean canalPronto(){return sender!=null&&!host.isBlank()&&!from.isBlank();}
    public void enviarTexto(String destino,String assunto,String texto){
        if(!canalPronto())throw new IllegalStateException("E-mail não configurado.");
        SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(destino);mail.setSubject(assunto);mail.setText(texto);sender.send(mail);
    }
    /* Mensagem do formulário de contato: vai para a caixa da TaskGO e a resposta volta para quem escreveu. */
    public void enviarContato(String destino,String responderPara,String assunto,String texto){
        if(!canalPronto())throw new IllegalStateException("E-mail não configurado.");
        SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(destino);mail.setReplyTo(responderPara);mail.setSubject(assunto);mail.setText(texto);sender.send(mail);
    }
    public void enviarConvite(String destino,String nome,String quemConvidou,String token){
        if(!configurado())throw new IllegalStateException("Canal de e-mail não configurado.");
        String separador=frontendUrl.contains("?")?"&":"?";
        String link=frontendUrl+separador+"token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8)+"&convite=1";
        SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(destino);mail.setSubject("Convite para a equipe da TaskGO");
        mail.setText("Olá"+(nome==null?"":", "+nome.trim().split(" +")[0])+".\n\n"+(quemConvidou==null?"Você":quemConvidou+" adicionou você")+" à equipe da TaskGO.\n\nCrie a sua senha neste link, em até 48 horas:\n\n"+link+"\n\nSe você não esperava este convite, ignore esta mensagem.");
        sender.send(mail);
    }
    public void enviarAvisoAlteracao(String destino){
        if(!configurado())return;
        SimpleMailMessage mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(destino);mail.setSubject("Senha alterada — TaskGO");mail.setText("A senha da sua conta TaskGO foi alterada. Se você não fez isso, solicite uma nova recuperação e entre em contato com o suporte.");sender.send(mail);
    }
}
