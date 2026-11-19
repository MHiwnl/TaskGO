package com.taskgo.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChamarAgoraController {
    /* Plano Pro: selo para o cliente. */
    private PlanoService planos;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPlanos(@org.springframework.context.annotation.Lazy PlanoService p) { this.planos = p; }
    private final ChamarAgoraService agora;
    private final PresencaProfissionalRepository presencas;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;

    public ChamarAgoraController(ChamarAgoraService agora, PresencaProfissionalRepository presencas, PedidoContratadoRepository pedidos, UsuarioRepository usuarios) {
        this.agora = agora; this.presencas = presencas; this.pedidos = pedidos; this.usuarios = usuarios;
    }

    /** O profissional online manda onde está (a cada ~30 s enquanto a área está aberta). */
    @PutMapping("/api/profissionais/eu/presenca")
    public Map<String, Object> presenca(@RequestBody PresencaRequest body, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        if (!"profissional".equalsIgnoreCase(p.tipo()) && !usuarios.findById(p.id()).map(AjudaRapida::ativo).orElse(false))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para profissionais ou para quem ligou a ajuda rápida.");
        if (body == null || Math.abs(body.latitude()) > 90 || Math.abs(body.longitude()) > 180 || body.accuracy() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posição inválida.");
        presencas.save(new PresencaProfissional(p.id(), body.latitude(), body.longitude(), Math.min(body.accuracy(), 100_000), System.currentTimeMillis()));
        return Map.of("ok", true);
    }

    /** Tela do cliente: quem está perto, em que onda está e, depois do aceite, o cartão do profissional. */
    @GetMapping("/api/chamar-agora/{pedidoId}")
    public Map<String, Object> situacao(@PathVariable String pedidoId, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!u.email().equalsIgnoreCase(String.valueOf(p.getClientEmail()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Map<String, Object> e = p.getExtra();
        Double lat = ChamarAgoraService.numero(e.get("lat")), lng = ChamarAgoraService.numero(e.get("lng"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", p.getId()); r.put("status", p.getStatus()); r.put("servico", p.getService()); r.put("valor", p.getPrice());
        r.put("lat", lat); r.put("lng", lng);
        int onda = e.get("onda") instanceof Number n ? n.intValue() : 0;
        long ultima = e.get("ultimaOnda") instanceof Number n ? n.longValue() : 0;
        r.put("onda", onda);
        r.put("chamados", ChamarAgoraService.ids(p, "notificados").size());
        r.put("semMais", Boolean.TRUE.equals(e.get("semMais")));
        r.put("proximaOndaEm", onda == 0 ? 0 : Math.max(0, ultima + ChamarAgoraService.INTERVALO_MS - System.currentTimeMillis()));
        r.put("criado", p.getCreated());
        /* No mapa: até 10 profissionais da onda atual (posição aproximada, ~1 km). */
        List<Map<String, Object>> perto = new ArrayList<>();
        if (lat != null && lng != null && p.getProEmail() == null) {
            List<Long> notificados = ChamarAgoraService.ids(p, "notificados");
            List<Long> daOnda = notificados.subList(Math.max(0, notificados.size() - ChamarAgoraService.POR_ONDA), notificados.size());
            List<ChamarAgoraService.Candidato> lista = agora.proximos(p.getService(), lat, lng, new HashSet<>(ChamarAgoraService.ids(p, "recusados")));
            lista.stream().filter(c -> daOnda.isEmpty() || daOnda.contains(c.usuario().getId())).limit(ChamarAgoraService.POR_ONDA).forEach(c -> {
                perto.add(pontoAproximado(c));
            });
        }
        r.put("perto", perto);
        if (p.getProEmail() != null) {
            usuarios.findByEmailIgnoreCase(p.getProEmail()).ifPresent(pro -> r.put("profissional", cartaoProfissional(pro, lat, lng)));
            r.put("confirmacao", e.getOrDefault("confirmacaoCliente", "confirmada"));
        }
        return r;
    }

    /** Foto, nota, serviços feitos e tempo de chegada: o que o cliente vê para decidir. */
    Map<String, Object> cartaoProfissional(Usuario pro, Double lat, Double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        Object[] resumo = ProfissionalController.resumo(pedidos.resumoAvaliacoes(pro.getEmail()));
        m.put("uid", pro.getId());
        m.put("nome", pro.getNome());
        m.put("verificado", VerificacaoController.verificado(pro));
        m.put("pro", planos != null && planos.ehPro(pro.getId()));
        m.put("nota", resumo[0] instanceof Number n ? Math.round(n.doubleValue() * 10) / 10.0 : null);
        m.put("avaliacoes", resumo[1] instanceof Number n ? n.intValue() : 0);
        m.put("servicos", pedidos.concluidosDoProfissional(pro.getEmail()));
        m.put("temFoto", FotoController.foto(pro) != null);
        presencas.findById(pro.getId()).ifPresent(x -> {
            m.put("lat", x.getLatitude()); m.put("lng", x.getLongitude());
            if (lat != null && lng != null) {
                double km = ChamarAgoraService.distanciaKm(lat, lng, x.getLatitude(), x.getLongitude());
                m.put("km", Math.round(km * 10) / 10.0); m.put("minutos", ChamarAgoraService.minutosAte(km));
            }
        });
        return m;
    }

    /** Antes de chamar: quantos profissionais da categoria estão online perto do ponto.
        Só para clientes, só até RAIO_PERTO_KM e com a posição aproximada (~1 km): ninguém consegue seguir um profissional pelo mapa. */
    @GetMapping("/api/chamar-agora/perto")
    public Map<String, Object> perto(@org.springframework.web.bind.annotation.RequestParam String servico,
                                     @org.springframework.web.bind.annotation.RequestParam double lat,
                                     @org.springframework.web.bind.annotation.RequestParam double lng, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        if (!"cliente".equalsIgnoreCase(u.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de cliente.");
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posição inválida.");
        List<ChamarAgoraService.Candidato> lista = agora.proximos(servico, lat, lng, new HashSet<>(java.util.List.of(u.id()))).stream().filter(c -> c.km() <= RAIO_PERTO_KM).toList();
        List<Map<String, Object>> pontos = new ArrayList<>();
        lista.stream().limit(ChamarAgoraService.POR_ONDA).forEach(c -> pontos.add(pontoAproximado(c)));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", lista.size());
        r.put("perto", pontos);
        r.put("maisPertoMin", lista.isEmpty() ? null : ChamarAgoraService.minutosAte(kmAproximado(lista.get(0).km())));
        return r;
    }

    static final double RAIO_PERTO_KM = 10;
    /* Posição com 2 casas (~1,1 km) e distância em km inteiros: serve para o mapa, não para localizar alguém. */
    static Map<String, Object> pontoAproximado(ChamarAgoraService.Candidato c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", Math.round(c.posicao().getLatitude() * 100) / 100.0);
        m.put("lng", Math.round(c.posicao().getLongitude() * 100) / 100.0);
        m.put("km", kmAproximado(c.km()));
        return m;
    }
    static double kmAproximado(double km) { return Math.max(1, Math.round(km)); }

    /** Lado do profissional: chamados que chegaram para ele e ainda estão valendo. */
    @GetMapping("/api/chamar-agora/convites")
    public List<Map<String, Object>> convites(Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        if (!"profissional".equalsIgnoreCase(u.tipo()) && !usuarios.findById(u.id()).map(AjudaRapida::ativo).orElse(false)) return List.of();
        PresencaProfissional eu = presencas.findById(u.id()).orElse(null);
        List<Map<String, Object>> r = new ArrayList<>();
        for (Pedido p : pedidos.abertosDesde(System.currentTimeMillis() - ChamarAgoraService.DURACAO_MS)) {
            if (!ChamarAgoraService.modoAgora(p) || p.getProEmail() != null || !ChamarAgoraService.convidado(p, u.id())) continue;
            OportunidadePedidoDto o = OportunidadePedidoDto.from(p);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId()); m.put("servico", p.getService()); m.put("valor", p.getPrice()); m.put("bairro", o.bairro()); m.put("dinheiro", DinheiroService.emDinheiro(p)); m.put("pixDireto", DinheiroService.pixDireto(p));
            m.put("descricao", o.notes()); m.put("criado", p.getCreated());
            Double lat = ChamarAgoraService.numero(p.getExtra().get("lat")), lng = ChamarAgoraService.numero(p.getExtra().get("lng"));
            if (eu != null && lat != null && lng != null) {
                double km = ChamarAgoraService.distanciaKm(lat, lng, eu.getLatitude(), eu.getLongitude());
                m.put("km", Math.round(km * 10) / 10.0); m.put("minutos", ChamarAgoraService.minutosAte(km));
            }
            usuarios.findByEmailIgnoreCase(String.valueOf(p.getClientEmail())).ifPresent(c -> {
                m.put("clienteNome", c.getNome() == null ? "Cliente" : c.getNome().trim().split("\\s+")[0]);
                m.put("clienteUid", c.getId());
                m.put("clienteTemFoto", FotoController.foto(c) != null);
            });
            r.add(m);
        }
        return r;
    }

    private TaskgoPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }

    public record PresencaRequest(double latitude, double longitude, double accuracy) {}
}
