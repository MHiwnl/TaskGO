package com.taskgo.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quanto a categoria custa de verdade na TaskGO: valores dos serviços concluídos e das propostas
 * aceitas no último ano. Com menos de 5 serviços, o site usa só a referência de mercado.
 */
@RestController
public class PrecoController {
    static final int AMOSTRA_MINIMA = 5;
    private final PedidoContratadoRepository pedidos;
    public PrecoController(PedidoContratadoRepository pedidos) { this.pedidos = pedidos; }

    @GetMapping("/api/precos/{servico}")
    public Map<String, Object> faixa(@PathVariable String servico) {
        long umAno = System.currentTimeMillis() - 365L * 24 * 3600 * 1000;
        List<Double> valores = new ArrayList<>(pedidos.precosConcluidos(servico, umAno).stream().filter(v -> v != null && v > 0).toList());
        valores.sort(Double::compare);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("servico", servico);
        r.put("amostras", valores.size());
        if (valores.size() >= AMOSTRA_MINIMA) {
            r.put("minimo", percentil(valores, .1));
            r.put("medio", percentil(valores, .5));
            r.put("maximo", percentil(valores, .9));
        }
        /* Duração em minutos; o percentil arredonda a 5 min, então serviços de 1 a 2 min aparecem como 0. */
        List<Double> duracoes = new ArrayList<>(pedidos.duracoesConcluidas(servico, umAno).stream().filter(v -> v != null && v > 0).map(v -> v / 60000.0).toList());
        duracoes.sort(Double::compare);
        if (duracoes.size() >= AMOSTRA_MINIMA) {
            r.put("duracaoMin", percentil(duracoes, .1));
            r.put("duracaoMedia", percentil(duracoes, .5));
            r.put("duracaoMax", percentil(duracoes, .9));
        }
        return r;
    }

    static double percentil(List<Double> ordenados, double p) {
        double pos = p * (ordenados.size() - 1);
        int i = (int) Math.floor(pos);
        double frac = pos - i;
        double v = i + 1 < ordenados.size() ? ordenados.get(i) + frac * (ordenados.get(i + 1) - ordenados.get(i)) : ordenados.get(i);
        return Math.round(v / 5) * 5.0;
    }
}
