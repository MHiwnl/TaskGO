package com.taskgo.api;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContatoService {
    private static final long WINDOW_MS=600_000;
    private static final int LIMIT=5;
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]{1,100}@[^\\s@.]{1,100}(?:\\.[^\\s@.]{1,63})+$");
    private final ContatoRepository repository;private final Clock clock;
    private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public ContatoService(ContatoRepository repository){this(repository,Clock.systemUTC());}
    ContatoService(ContatoRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    private EmailRecuperacaoSenha email;
    @org.springframework.beans.factory.annotation.Autowired(required=false) void setEmail(EmailRecuperacaoSenha email){this.email=email;}
    private String destino="taskgo.adm@gmail.com";
    @org.springframework.beans.factory.annotation.Autowired(required=false) void setDestino(@org.springframework.beans.factory.annotation.Value("${taskgo.contato.email:taskgo.adm@gmail.com}") String destino){if(destino!=null&&!destino.isBlank())this.destino=destino.trim();}

    public ContatoResponse receber(ContatoRequest request,String clientIp){
        if(request==null||blank(request.nome())||request.nome().trim().length()>120||request.email()==null||request.email().length()>254||!EMAIL.matcher(request.email().trim()).matches()
                ||blank(request.assunto())||request.assunto().trim().length()>120||blank(request.mensagem())||request.mensagem().trim().length()>5000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Revise nome, e-mail, assunto e mensagem.");
        if(request.website()!=null&&!request.website().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solicitacao invalida.");
        limitar(clientIp==null?"desconhecido":clientIp);
        long now=clock.millis();Contato saved=repository.save(new Contato(java.util.UUID.randomUUID().toString(),request.nome().trim(),request.email().trim().toLowerCase(java.util.Locale.ROOT),request.assunto().trim(),request.mensagem().trim(),now));
        if(email!=null&&email.canalPronto()){
            try{
                email.enviarContato(destino,saved.getEmail(),"[Contato TaskGO] "+saved.getAssunto()+" — "+saved.getNome(),
                        "Nome: "+saved.getNome()+"\nE-mail: "+saved.getEmail()+"\nAssunto: "+saved.getAssunto()+"\n\n"+saved.getMensagem()+"\n\nResponda este e-mail para falar com a pessoa.");
                saved.emailStatus("enviado");
            }catch(RuntimeException falha){saved.emailStatus("falhou");}
            saved=repository.save(saved);
        }
        return new ContatoResponse(saved.getId(),saved.getCriadaEm(),saved.getStatus(),saved.getEmailStatus());
    }
    private synchronized void limitar(String ip){
        long now=clock.millis();attempts.entrySet().removeIf(entry->now-entry.getValue().startedAt>=WINDOW_MS);
        if(!attempts.containsKey(ip)&&attempts.size()>=10_000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Limite temporario de solicitacoes. Tente novamente mais tarde.");
        Attempt a=attempts.compute(ip,(key,old)->{if(old==null)return new Attempt(now,new AtomicInteger(1));old.count.incrementAndGet();return old;});
        if(a.count.get()>LIMIT)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Limite de mensagens atingido. Tente novamente mais tarde.");
    }
    private boolean blank(String s){return s==null||s.isBlank();}
    private record Attempt(long startedAt,AtomicInteger count){}
    public record ContatoRequest(String nome,String email,String assunto,String mensagem,String website){}
    public record ContatoResponse(String id,long criadaEm,String status,String emailStatus){}
}
