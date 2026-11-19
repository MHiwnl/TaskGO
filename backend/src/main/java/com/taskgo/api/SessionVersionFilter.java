package com.taskgo.api;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionVersionFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;
    public SessionVersionFilter(UsuarioRepository usuarios){this.usuarios=usuarios;}
    /* Sessões encerradas pela pessoa (em Perfil) caem aqui; a última atividade é anotada no máximo uma vez por minuto. */
    private SessaoContaRepository sessoes;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSessoes(SessaoContaRepository sessoes){this.sessoes=sessoes;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        HttpSession session=request.getSession(false);
        boolean backedBySession=session!=null&&session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)!=null;
        if(backedBySession&&auth!=null&&auth.getPrincipal() instanceof TaskgoPrincipal p&&p.credentialVersion()>=0){
            boolean valida=usuarios.findById(p.id()).map(u->u.getCredentialVersion()==p.credentialVersion()).orElse(false);
            if(valida&&sessoes!=null&&session.getAttribute(SegurancaContaController.SESSAO) instanceof String sid){
                SessaoConta sc=sessoes.findById(sid).orElse(null);
                if(sc==null||!sc.ativa())valida=false;
                else{long agora=System.currentTimeMillis();if(agora-sc.getVistaEm()>60_000){sc.ver(agora);sessoes.save(sc);}}
            }
            if(!valida){SecurityContextHolder.clearContext();if(session!=null)session.invalidate();response.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Sessão encerrada. Entre novamente.");return;}
        }
        chain.doFilter(request,response);
    }
}
