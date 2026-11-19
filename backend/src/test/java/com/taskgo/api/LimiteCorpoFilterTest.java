package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteCorpoFilterTest {
    private MockHttpServletResponse enviar(String caminho, int bytes) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", caminho);
        req.setContent(new byte[bytes]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new LimiteCorpoFilter().doFilter(req, res, chain);
        if (chain.getRequest() != null) assertEquals(bytes, chain.getRequest().getInputStream().readAllBytes().length);
        return res;
    }

    @Test void dadosDaContaAte1MB() throws Exception {
        assertEquals(200, enviar("/api/auth/dados", 200 * 1024).getStatus());
        assertEquals(413, enviar("/api/auth/dados", 1024 * 1024 + 1).getStatus());
    }

    @Test void restoDaApiAte2MB() throws Exception {
        assertEquals(200, enviar("/api/pedidos/contratados", 1024 * 1024 + 1).getStatus());
        assertEquals(413, enviar("/api/pedidos/contratados", 2 * 1024 * 1024 + 1).getStatus());
    }
}
