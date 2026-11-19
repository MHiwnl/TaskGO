package com.taskgo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/* O código de chegada é a prova de que o profissional está diante do cliente:
   em toda resposta com pedidos, ele só vai para o cliente dono do pedido. */
@RestControllerAdvice
public class PedidoRespostaAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;
    public PedidoRespostaAdvice(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Pedido p) return semCodigoSeNaoForCliente(p);
        if (body instanceof Collection<?> lista && !lista.isEmpty() && lista.stream().anyMatch(Pedido.class::isInstance))
            return lista.stream().map(x -> x instanceof Pedido p ? semCodigoSeNaoForCliente(p) : x).toList();
        return body;
    }

    private Object semCodigoSeNaoForCliente(Pedido p) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null && auth.getPrincipal() instanceof TaskgoPrincipal t ? t.email() : null;
        if (email != null && p.getClientEmail() != null && email.trim().toLowerCase(Locale.ROOT).equals(p.getClientEmail().trim().toLowerCase(Locale.ROOT))) return p;
        @SuppressWarnings("unchecked") Map<String, Object> copia = mapper.convertValue(p, Map.class);
        copia.remove("code");
        return copia;
    }
}
