package com.taskgo.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Chamar Agora (estilo aplicativo de corrida): o pedido vai em ondas para os profissionais online
 * mais perto, 10 por vez, a cada 20 segundos. O primeiro que aceita fica com o serviço; para os outros
 * o chamado some. O cliente confere quem aceitou e pode recusar; aí as ondas continuam.
 *
 * Estado guardado nos dados extras do pedido:
 *   modo = "agora", notificados = [ids], recusados = [ids], onda = n, ultimaOnda = ms, semMais = true.
 */
@Service
public class ChamarAgoraService {
    private static final Logger log = LoggerFactory.getLogger(ChamarAgoraService.class);
    public static final int POR_ONDA = 10;
    public static final long INTERVALO_MS = 20_000, PRESENCA_VALIDA_MS = 10 * 60_000, DURACAO_MS = 20 * 60_000;
    public static final double RAIO_KM = 40, VELOCIDADE_KMH = 25;
    private static final JsonMapConverter JSON = new JsonMapConverter();

    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final PresencaProfissionalRepository presencas;
    private final EventosService eventos;
    private final NotificacaoService notificacoes;
    private final RankingProfissionais ranking;

    public ChamarAgoraService(PedidoContratadoRepository pedidos, UsuarioRepository usuarios, PresencaProfissionalRepository presencas,
                              EventosService eventos, NotificacaoService notificacoes, RankingProfissionais ranking) {
        this.pedidos = pedidos; this.usuarios = usuarios; this.presencas = presencas; this.eventos = eventos; this.notificacoes = notificacoes; this.ranking = ranking;
    }

    public static boolean modoAgora(Pedido p) { return p != null && "agora".equals(p.getExtra().get("modo")); }

    public static List<Long> ids(Pedido p, String chave) {
        List<Long> r = new ArrayList<>();
        if (p.getExtra().get(chave) instanceof List<?> l) for (Object o : l) if (o instanceof Number n) r.add(n.longValue());
        return r;
    }

    /** O profissional recebeu este chamado (e ainda pode aceitá-lo). */
    public static boolean convidado(Pedido p, Long usuarioId) { return ids(p, "notificados").contains(usuarioId) && !ids(p, "recusados").contains(usuarioId); }

    public static double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Minutos até chegar, em trânsito urbano (mínimo de 2). */
    public static int minutosAte(double km) { return (int) Math.max(2, Math.round(km / VELOCIDADE_KMH * 60)); }

    static Double numero(Object o) { return o instanceof Number n ? n.doubleValue() : null; }

    /** Profissionais online da categoria com posição recente, do mais perto ao mais longe. */
    private AgendaService agenda;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setAgendaService(AgendaService agenda) { this.agenda = agenda; }
    private ExigenciaVerificacao exigencia;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }

    public List<Candidato> proximos(String servico, double lat, double lng, Set<Long> excluir) {
        long agora = System.currentTimeMillis();
        Map<Long, PresencaProfissional> pos = presencas.findByAtualizadaEmGreaterThan(agora - PRESENCA_VALIDA_MS).stream()
                .collect(Collectors.toMap(PresencaProfissional::getUsuarioId, x -> x));
        if (pos.isEmpty()) return List.of();
        /* Ajuda rápida: qualquer pessoa verificada com a ajuda ligada; demais serviços: profissionais online da categoria. */
        java.util.stream.Stream<Usuario> base = AjudaRapida.SERVICO.equals(servico)
                ? usuarios.findAllById(pos.keySet()).stream().filter(AjudaRapida::podeAjudar)
                : usuarios.profissionaisDoServico(servico).stream().filter(DisponibilidadeController::online)
                        .filter(u -> exigencia == null || exigencia.liberado(u));
        return base
                .filter(u -> pos.containsKey(u.getId()) && !excluir.contains(u.getId()))
                .filter(u -> !Boolean.TRUE.equals(u.getExtra().get(ModeracaoService.BANIDO)))
                .filter(u -> agenda == null || !agenda.bloqueadoAgora(u.getId()))
                .map(u -> { PresencaProfissional x = pos.get(u.getId()); return new Candidato(u, x, distanciaKm(lat, lng, x.getLatitude(), x.getLongitude())); })
                .filter(c -> c.km() <= RAIO_KM)
                .sorted(Comparator.comparingDouble(Candidato::km))
                .toList();
    }

    public record Candidato(Usuario usuario, PresencaProfissional posicao, double km) {}

    /* Plano Pro: recebe o chamado uma onda antes. Na lista por distância, quem é Pro conta como se estivesse
       POR_ONDA posições mais perto (o 15º mais perto, se for Pro, entra na 1ª onda); quem não é Pro mantém a ordem. */
    static <T> List<T> ordemDaOnda(List<T> porDistancia, java.util.function.Predicate<T> pro) {
        List<Integer> ordem = new ArrayList<>();
        for (int i = 0; i < porDistancia.size(); i++) ordem.add(i);
        ordem.sort(Comparator.comparingInt(i -> i - (pro.test(porDistancia.get(i)) ? POR_ONDA : 0)));
        return ordem.stream().map(porDistancia::get).toList();
    }
    private PlanoService planos;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPlanos(@org.springframework.context.annotation.Lazy PlanoService p) { this.planos = p; }

    /** Roda a cada 4 segundos: manda a próxima onda dos pedidos que estão chamando. */
    @Scheduled(fixedDelay = 4_000, initialDelay = 10_000)
    public void despachar() {
        long agora = System.currentTimeMillis();
        try {
            for (Pedido p : pedidos.abertosDesde(agora - DURACAO_MS)) if (modoAgora(p) && p.getProEmail() == null) proximaOnda(p, agora);
        } catch (RuntimeException e) { log.warn("Falha ao despachar o Chamar Agora: {}", e.getMessage()); }
    }

    /** Começa na hora (sem esperar o agendador) quando o pedido é criado ou volta ao mural. */
    public void iniciar(Pedido p) { if (modoAgora(p)) proximaOnda(p, System.currentTimeMillis()); }

    void proximaOnda(Pedido p, long agora) {
        Map<String, Object> e = p.getExtra();
        int onda = e.get("onda") instanceof Number n ? n.intValue() : 0;
        long ultima = e.get("ultimaOnda") instanceof Number n ? n.longValue() : 0;
        if (onda > 0 && agora - ultima < INTERVALO_MS) return;
        Double lat = numero(e.get("lat")), lng = numero(e.get("lng"));
        if (lat == null || lng == null) return;
        List<Long> notificados = ids(p, "notificados");
        Set<Long> fora = new java.util.HashSet<>(notificados); fora.addAll(ids(p, "recusados"));
        try { fora.add(Long.valueOf(p.getClientId())); } catch (RuntimeException ignorar) { /* pedido sem id de cliente */ }
        List<Candidato> perto = proximos(p.getService(), lat, lng, fora);
        Set<Long> pros = planos == null ? Set.of() : planos.pros(perto.stream().map(c -> c.usuario().getId()).toList());
        /* Dentro da onda, quem tem mais qualidade e está mais perto vem antes; o Pro ainda antecipa uma onda. */
        Map<Long, RankingProfissionais.Metricas> qualidade = ranking.metricas(perto.stream().map(Candidato::usuario).toList());
        List<Candidato> porQualidade = perto.stream().sorted(Comparator.comparingDouble((Candidato c) -> -RankingProfissionais.pontuacaoOnda(c.km(), RAIO_KM, qualidade.get(c.usuario().getId())))).toList();
        List<Candidato> novos = ordemDaOnda(porQualidade, c -> pros.contains(c.usuario().getId())).stream().limit(POR_ONDA).toList();
        Map<String, Object> extra = new LinkedHashMap<>(e);
        if (novos.isEmpty()) {
            if (onda > 0 && !Boolean.TRUE.equals(e.get("semMais"))) { extra.put("semMais", true); gravar(p, extra); }
            else if (onda == 0) { extra.put("onda", 0); extra.put("ultimaOnda", agora); extra.put("semMais", true); gravar(p, extra); }
            return;
        }
        List<Long> todos = new ArrayList<>(notificados);
        novos.forEach(c -> todos.add(c.usuario().getId()));
        extra.put("notificados", todos); extra.put("onda", onda + 1); extra.put("ultimaOnda", agora); extra.remove("semMais");
        if (!gravar(p, extra)) return;
        String valor = p.getPrice() == null ? "" : String.format(Locale.forLanguageTag("pt-BR"), " · R$ %.2f", p.getPrice());
        for (Candidato c : novos) {
            eventos.enviar(c.usuario().getId(), "chamar-agora", p.getId());
            try {
                notificacoes.criar(c.usuario().getId(), p.getId(), "pedido", "Chamado agora perto de você",
                        "Um cliente a " + String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", c.km()) + " precisa de você agora" + valor + ".", "agora:" + p.getId());
            } catch (RuntimeException ignorar) { /* notificação repetida: o aviso em tempo real já foi */ }
        }
    }

    private boolean gravar(Pedido p, Map<String, Object> extra) {
        boolean ok = pedidos.gravarExtraSeAberto(p.getId(), JSON.convertToDatabaseColumn(extra)) > 0;
        if (ok) { p.getExtra().clear(); p.getExtra().putAll(extra); }
        return ok;
    }

    /** Depois do aceite: o chamado some para os outros profissionais que o receberam. */
    public void encerrarParaOsOutros(Pedido p, Long quemFicou) {
        for (Long id : ids(p, "notificados")) if (!id.equals(quemFicou)) eventos.enviar(id, "chamar-agora-encerrado", p.getId());
    }

    /** O cliente recusou quem aceitou: o pedido volta a chamar, sem essa pessoa, e o chamado reaparece para os demais. */
    public void reabrir(Pedido p, Long recusado) {
        Map<String, Object> extra = new LinkedHashMap<>(p.getExtra());
        List<Long> recusados = ids(p, "recusados"); if (!recusados.contains(recusado)) recusados.add(recusado);
        extra.put("recusados", recusados); extra.remove("confirmacaoCliente"); extra.remove("semMais");
        extra.put("ultimaOnda", 0L);
        gravar(p, extra);
        if (modoAgora(p)) for (Long id : ids(p, "notificados")) if (!recusados.contains(id)) eventos.enviar(id, "chamar-agora", p.getId());
    }
}
