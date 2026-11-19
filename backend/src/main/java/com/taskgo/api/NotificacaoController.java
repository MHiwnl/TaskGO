package com.taskgo.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/notificacoes")
public class NotificacaoController {
    private final NotificacaoService service;
    public NotificacaoController(NotificacaoService service){this.service=service;}
    private UsuarioRepository usuarios;
    @org.springframework.beans.factory.annotation.Autowired(required=false) void setUsuarios(UsuarioRepository usuarios){this.usuarios=usuarios;}
    @GetMapping public List<NotificacaoService.NotificacaoDto> listar(Authentication auth){
        TaskgoPrincipal p=principal(auth);
        lembrarFoto(p);
        return service.listar(p.id()).stream().map(NotificacaoService.NotificacaoDto::from).toList();
    }
    /* Conta: quem atende ou contrata sem foto recebe um lembrete; com a foto posta, o lembrete se resolve sozinho. */
    private void lembrarFoto(TaskgoPrincipal p){
        if(usuarios==null||!java.util.Set.of("cliente","profissional").contains(String.valueOf(p.tipo()).toLowerCase(java.util.Locale.ROOT)))return;
        usuarios.findById(p.id()).ifPresent(u->{
            boolean temFoto=FotoController.foto(u)!=null;
            if(!temFoto)service.criar(p.id(),null,"conta","Coloque uma foto de perfil",
                "profissional".equalsIgnoreCase(u.getTipo())?"Clientes aceitam mais rápido quem mostra o rosto. Toque para pôr sua foto.":"O profissional confere quem chamou antes de sair. Toque para pôr sua foto.","conta:foto");
            else service.resolver(p.id(),"conta:foto");
        });
    }
    @GetMapping("/nao-lidas") public CountDto naoLidas(Authentication auth){return new CountDto(service.naoLidas(principal(auth).id()));}
    @PatchMapping("/{id}/lida") public NotificacaoService.NotificacaoDto marcarLida(@PathVariable String id,Authentication auth){return NotificacaoService.NotificacaoDto.from(service.marcarLida(principal(auth).id(),id));}
    @PatchMapping("/lidas") @ResponseStatus(HttpStatus.NO_CONTENT) public void marcarTodas(Authentication auth){service.marcarTodas(principal(auth).id());}
    private TaskgoPrincipal principal(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof TaskgoPrincipal p))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);return p;}
    public record CountDto(long count){}
}
