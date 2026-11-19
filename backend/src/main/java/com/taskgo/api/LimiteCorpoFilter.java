package com.taskgo.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/* Tamanho máximo do corpo das requisições da API: 2 MB em geral e 1 MB para os dados da conta.
   Vale também para corpo enviado em pedaços (sem Content-Length): lê no máximo o limite + 1 byte. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LimiteCorpoFilter extends OncePerRequestFilter {
    static final int LIMITE_GERAL = 2 * 1024 * 1024;
    static final int LIMITE_DADOS_CONTA = 1024 * 1024;

    static final int LIMITE_VERIFICACAO = 5 * 1024 * 1024;
    static int limite(String caminho) { return "/api/auth/dados".equals(caminho) ? LIMITE_DADOS_CONTA : "/api/verificacao".equals(caminho) ? LIMITE_VERIFICACAO : LIMITE_GERAL; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        return !request.getRequestURI().startsWith("/api/") || !("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        int max = limite(request.getRequestURI());
        long declarado = request.getContentLengthLong();
        if (declarado > max) { recusar(response, max); return; }
        byte[] corpo = request.getInputStream().readNBytes(max + 1);
        if (corpo.length > max) { recusar(response, max); return; }
        chain.doFilter(new CorpoLido(request, corpo), response);
    }

    private static void recusar(HttpServletResponse response, int max) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":413,\"message\":\"Dados grandes demais (limite de " + (max / (1024 * 1024)) + " MB).\"}");
    }

    private static final class CorpoLido extends HttpServletRequestWrapper {
        private final byte[] corpo;
        CorpoLido(HttpServletRequest r, byte[] corpo) { super(r); this.corpo = corpo; }
        @Override public ServletInputStream getInputStream() {
            InputStream in = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override public int read() throws IOException { return in.read(); }
                @Override public int read(byte[] b, int off, int len) throws IOException { return in.read(b, off, len); }
                @Override public boolean isFinished() { try { return in.available() == 0; } catch (IOException e) { return true; } }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() {
            String cs = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), cs == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(cs)));
        }
        @Override public int getContentLength() { return corpo.length; }
        @Override public long getContentLengthLong() { return corpo.length; }
    }
}
