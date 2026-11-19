package com.taskgo.api;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {
    private static final long WINDOW_MS = Duration.ofMinutes(5).toMillis();
    private static final int MAX_ATTEMPTS = 10;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    public boolean allowed(String address) {
        long now = System.currentTimeMillis();
        Window window = attempts.compute(address, (key, old) -> old == null || now - old.startedAt >= WINDOW_MS
                ? new Window(now, new AtomicInteger()) : old);
        return window.count.get() < MAX_ATTEMPTS;
    }

    public void failed(String address) {
        long now = System.currentTimeMillis();
        Window window = attempts.compute(address, (key, old) -> old == null || now - old.startedAt >= WINDOW_MS
                ? new Window(now, new AtomicInteger()) : old);
        window.count.incrementAndGet();
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MS);
    }

    public void succeeded(String address) { attempts.remove(address); }

    /* ---- Por conta: no máximo 3 senhas erradas a cada 10 minutos para o mesmo e-mail (vale de qualquer IP). ---- */
    static final long JANELA_CONTA_MS = Duration.ofMinutes(10).toMillis();
    static final int MAX_ERROS_CONTA = 3;
    private final ConcurrentHashMap<String, Window> porConta = new ConcurrentHashMap<>();
    private static String chave(String email) { return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT); }

    /** Milissegundos até a conta poder tentar de novo; 0 se pode tentar agora. */
    public long contaBloqueadaPor(String email) {
        long now = System.currentTimeMillis();
        Window w = porConta.get(chave(email));
        if (w == null || now - w.startedAt >= JANELA_CONTA_MS) return 0;
        return w.count.get() >= MAX_ERROS_CONTA ? w.startedAt + JANELA_CONTA_MS - now : 0;
    }
    public void contaErrou(String email) {
        long now = System.currentTimeMillis();
        porConta.compute(chave(email), (k, old) -> old == null || now - old.startedAt >= JANELA_CONTA_MS ? new Window(now, new AtomicInteger()) : old).count.incrementAndGet();
        if (porConta.size() > 50_000) porConta.entrySet().removeIf(e -> now - e.getValue().startedAt >= JANELA_CONTA_MS);
    }
    public void contaAcertou(String email) { porConta.remove(chave(email)); }

    private record Window(long startedAt, AtomicInteger count) {}
}
