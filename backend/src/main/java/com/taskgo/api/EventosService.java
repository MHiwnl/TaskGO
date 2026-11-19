package com.taskgo.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/* Tempo real por Server-Sent Events: cada conta aberta no site recebe avisos curtos
   ("oportunidade", "proposta", "pedido", "mensagem", "moderacao") e busca os dados de novo.
   O aviso não carrega dados: quem decide o que a conta pode ver continua sendo cada endpoint. */
@Service
@EnableScheduling
public class EventosService {
    private final Map<Long, List<SseEmitter>> conexoes = new ConcurrentHashMap<>();

    public SseEmitter abrir(Long usuarioId) {
        SseEmitter e = new SseEmitter(0L);
        conexoes.computeIfAbsent(usuarioId, k -> new CopyOnWriteArrayList<>()).add(e);
        Runnable remover = () -> { List<SseEmitter> l = conexoes.get(usuarioId); if (l != null) l.remove(e); };
        e.onCompletion(remover); e.onTimeout(remover); e.onError(t -> remover.run());
        try { e.send(SseEmitter.event().name("pronto").data("ok")); } catch (IOException ex) { remover.run(); }
        return e;
    }

    public void enviar(Long usuarioId, String tipo, String pedidoId) {
        if (usuarioId == null) return;
        List<SseEmitter> l = conexoes.get(usuarioId);
        if (l == null) return;
        for (SseEmitter e : l) {
            try { e.send(SseEmitter.event().name(tipo).data(pedidoId == null ? "" : pedidoId)); }
            catch (IOException | IllegalStateException ex) { l.remove(e); }
        }
    }

    public void enviarParaTodos(Iterable<Long> usuarios, String tipo, String pedidoId) { for (Long u : usuarios) enviar(u, tipo, pedidoId); }

    /* Mantém a conexão viva atrás de proxies que derrubam conexões paradas. */
    @Scheduled(fixedRate = 25_000)
    public void manterVivo() {
        conexoes.forEach((id, l) -> l.forEach(e -> {
            try { e.send(SseEmitter.event().comment("ping")); } catch (IOException | IllegalStateException ex) { l.remove(e); }
        }));
    }

    @RestController
    public static class EventosController {
        private final EventosService eventos;
        public EventosController(EventosService eventos) { this.eventos = eventos; }

        @GetMapping(value = "/api/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter assinar(Authentication auth) {
            if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            return eventos.abrir(p.id());
        }
    }
}
