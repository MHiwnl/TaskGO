package com.taskgo.api;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Negociação de valor de um pedido ainda sem profissional.
   O profissional propõe; o cliente aceita, recusa ou faz contraproposta para aquele profissional;
   quem fez a proposta pode cancelá-la enquanto está pendente. Aceitar fecha o pedido com aquele valor. */
@RestController
@RequestMapping("/api/pedidos/contratados/{pedidoId}/propostas")
public class PropostaController {
    static final double VALOR_MAXIMO = 100_000;
    private final PropostaRepository propostas;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final NotificacaoService notificacoes;
    private final ModeracaoService moderacao;
    private final EventosService eventos;
    private PagamentoService pagamentos;
    private AgendaService agenda;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setAgendaService(AgendaService agenda) { this.agenda = agenda; }
    private ExigenciaVerificacao exigencia;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPagamentos(PagamentoService pagamentos) { this.pagamentos = pagamentos; }
    private ServicosProfissional servicosPro;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setServicosPro(ServicosProfissional s) { this.servicosPro = s; }
    private RankingProfissionais ranking;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setRanking(RankingProfissionais r) { this.ranking = r; }
    private PresencaProfissionalRepository presencas;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPresencas(PresencaProfissionalRepository p) { this.presencas = p; }

    public PropostaController(PropostaRepository propostas, PedidoContratadoRepository pedidos, UsuarioRepository usuarios,
                              NotificacaoService notificacoes, ModeracaoService moderacao, EventosService eventos) {
        this.propostas = propostas; this.pedidos = pedidos; this.usuarios = usuarios; this.notificacoes = notificacoes;
        this.moderacao = moderacao; this.eventos = eventos;
    }

    /* Cliente vê todas as propostas do pedido; profissional vê só a própria negociação. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<PropostaDto> listar(@PathVariable String pedidoId, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        Pedido p = pedido(pedidoId);
        if (dono(p, u)) return comparavel(propostas.findByPedidoIdOrderByCriadaEmAsc(pedidoId), p);
        if ("profissional".equalsIgnoreCase(u.tipo())) return propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc(pedidoId, u.id()).stream().map(this::dto).toList();
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /* Profissional manda uma proposta, ou cliente manda contraproposta a um profissional (profissionalId). */
    @PostMapping
    @Transactional
    public PropostaDto enviar(@PathVariable String pedidoId, @RequestBody NovaProposta body, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        moderacao.exigirLiberado(u.id());
        if (exigencia != null && "profissional".equalsIgnoreCase(u.tipo())) exigencia.exigirParaAtender(u.id());
        Pedido p = pedidoAberto(pedidoId);
        if (ObraService.eh(p)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Em obras, mande um orçamento com etapas.");
        if (RecorrenteService.eh(p)) throw new ResponseStatusException(HttpStatus.CONFLICT, "No serviço recorrente, o valor de cada vez é o do pedido.");
        if (body != null && body.valor() != null && body.valor() >= 3000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A partir de R$ 3.000, o serviço vira obra: o cliente precisa pedir orçamento.");
        if (body != null && body.valor() != null && body.valor() >= 1000 && DinheiroService.emDinheiro(p)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este pedido é em dinheiro, que vale só abaixo de R$ 1.000.");
        if (body == null || body.valor() == null || body.valor() <= 0 || body.valor() > VALOR_MAXIMO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um valor entre R$ 1 e R$ 100.000.");
        if ("profissional".equalsIgnoreCase(u.tipo())) validarTermos(body);
        double valor = Math.round(body.valor() * 100) / 100.0;
        Usuario profissional;
        String autor;
        if ("profissional".equalsIgnoreCase(u.tipo())) {
            profissional = usuarios.findById(u.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            if (!atende(profissional, p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este profissional não atende a categoria deste pedido.");
            if (!DisponibilidadeController.online(profissional))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Você está offline. Fique online para enviar propostas.");
            if (agenda != null) agenda.exigirLivre(profissional, p);
            autor = "profissional";
        } else if (dono(p, u)) {
            if (body.profissionalId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o profissional da contraproposta.");
            if (propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc(pedidoId, body.profissionalId()).isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível contrapropor a quem já mandou proposta.");
            profissional = usuarios.findById(body.profissionalId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
            if (!atende(profissional, p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este profissional não atende a categoria deste pedido.");
            autor = "cliente";
        } else throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        long agora = System.currentTimeMillis();
        /* A nova proposta substitui a pendente anterior do mesmo par. */
        propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc(pedidoId, profissional.getId()).stream()
                .filter(Proposta::pendente).forEach(x -> x.encerrar(Proposta.SUBSTITUIDA, agora));
        boolean doProfissional = "profissional".equals(autor);
        Proposta nova = propostas.save(new Proposta(java.util.UUID.randomUUID().toString(), pedidoId, profissional, autor, valor, agora,
                doProfissional ? body.prazoDias() : null, doProfissional ? body.garantiaDias() : null, doProfissional ? body.materialIncluso() : null));

        Long destino = "profissional".equals(autor) ? idCliente(p) : profissional.getId();
        String valorTexto = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", valor);
        if (destino != null) {
            notificacoes.criar(destino, pedidoId, "proposta", "profissional".equals(autor) ? "Nova proposta" : "Contraproposta do cliente",
                    ("profissional".equals(autor) ? profissional.getNome() + " propôs " : "O cliente propôs ") + valorTexto + ".", "proposta:" + nova.getId());
            eventos.enviar(destino, "proposta", pedidoId);
        }
        return dto(nova);
    }

    @PostMapping("/{propostaId}/aceitar")
    @Transactional
    public Pedido aceitar(@PathVariable String pedidoId, @PathVariable String propostaId, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        moderacao.exigirLiberado(u.id());
        Pedido p = pedidoAberto(pedidoId);
        Proposta x = respondivel(pedidoId, propostaId, u, p);
        Usuario profissional = usuarios.findById(x.getProfissionalId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (agenda != null) agenda.exigirLivre(profissional, p);
        long agora = System.currentTimeMillis();
        int ok = pedidos.aceitarSeDisponivel(pedidoId, profissional.getEmail().trim().toLowerCase(Locale.ROOT), profissional.getProId(), profissional.getNome(), agora);
        if (ok == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido não está mais disponível.");
        Pedido fechado = pedidos.findById(pedidoId).orElseThrow();
        fechado.setPrice(x.getValor());
        /* Os termos combinados são os da última proposta do profissional; a contraproposta do cliente não traz os seus.
           Chave só do servidor: o cliente não a grava (PedidoService.EXTRAS_DO_CLIENTE não a inclui). */
        Proposta fonte = propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc(pedidoId, x.getProfissionalId()).stream()
                .filter(o -> "profissional".equals(o.getAutor())).reduce((a, b) -> b).orElse(null);
        if (fonte != null && fonte.getPrazoDias() != null) {
            Map<String, Object> combinado = new LinkedHashMap<>();
            combinado.put("prazoDias", fonte.getPrazoDias());
            combinado.put("garantiaDias", fonte.getGarantiaDias());
            combinado.put("materialIncluso", fonte.getMaterialIncluso());
            fechado.getExtra().put("combinado", combinado);
        }
        pedidos.save(fechado);
        /* Valor maior que o pago: cobra a diferença (a sobra de um valor menor volta ao concluir). */
        if (pagamentos != null) pagamentos.aposMudarValor(fechado);
        /* A atualização do pedido limpa a sessão do JPA: a proposta é relida antes de ser marcada como aceita,
           e fica fora da recusa das outras pendentes. */
        Proposta aceita = propostas.findById(x.getId()).orElseThrow();
        aceita.encerrar(Proposta.ACEITA, agora);
        propostas.save(aceita);
        propostas.findByPedidoIdAndStatus(pedidoId, Proposta.PENDENTE).stream()
                .filter(o -> !o.getId().equals(aceita.getId())).forEach(o -> { o.encerrar(Proposta.RECUSADA, agora); propostas.save(o); });

        Long cliente = idCliente(p);
        Long outro = "profissional".equals(x.getAutor()) ? profissional.getId() : cliente;
        if (outro != null) {
            notificacoes.criar(outro, pedidoId, "proposta", "Proposta aceita", "A proposta foi aceita. O atendimento começou.", "proposta-aceita:" + x.getId());
            eventos.enviar(outro, "pedido", pedidoId);
        }
        return fechado;
    }

    @PostMapping("/{propostaId}/recusar")
    @Transactional
    public PropostaDto recusar(@PathVariable String pedidoId, @PathVariable String propostaId, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        Pedido p = pedidoAberto(pedidoId);
        Proposta x = respondivel(pedidoId, propostaId, u, p);
        x.encerrar(Proposta.RECUSADA, System.currentTimeMillis());
        Long autor = "profissional".equals(x.getAutor()) ? x.getProfissionalId() : idCliente(p);
        if (autor != null) eventos.enviar(autor, "proposta", pedidoId);
        return dto(x);
    }

    @PostMapping("/{propostaId}/cancelar")
    @Transactional
    public PropostaDto cancelar(@PathVariable String pedidoId, @PathVariable String propostaId, Authentication auth) {
        TaskgoPrincipal u = principal(auth);
        Pedido p = pedido(pedidoId);
        Proposta x = propostas.findById(propostaId).filter(o -> o.getPedidoId().equals(pedidoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta não encontrada."));
        boolean autor = "profissional".equals(x.getAutor()) ? u.id().equals(x.getProfissionalId()) : dono(p, u);
        if (!autor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só quem fez a proposta pode cancelá-la.");
        if (!x.pendente()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta proposta já foi respondida.");
        x.encerrar(Proposta.CANCELADA, System.currentTimeMillis());
        Long outro = "profissional".equals(x.getAutor()) ? idCliente(p) : x.getProfissionalId();
        if (outro != null) eventos.enviar(outro, "proposta", pedidoId);
        return dto(x);
    }

    /* A proposta precisa estar pendente, e quem responde é o outro lado da negociação. */
    private Proposta respondivel(String pedidoId, String propostaId, TaskgoPrincipal u, Pedido p) {
        Proposta x = propostas.findById(propostaId).filter(o -> o.getPedidoId().equals(pedidoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta não encontrada."));
        if (!x.pendente()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta proposta já foi respondida.");
        boolean podeResponder = "profissional".equals(x.getAutor()) ? dono(p, u) : u.id().equals(x.getProfissionalId());
        if (!podeResponder) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quem responde é o outro lado da negociação.");
        return x;
    }

    /* Ajuda rápida não é categoria do catálogo: não passa pela checagem (igual à ObraService). */
    private boolean atende(Usuario pro, Pedido p) {
        if (AjudaRapida.eh(p)) return true;
        return servicosPro != null ? servicosPro.faz(pro, p.getService()) : String.valueOf(p.getService()).equalsIgnoreCase(String.valueOf(pro.getService()));
    }
    private Pedido pedido(String id) { return pedidos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.")); }
    private Pedido pedidoAberto(String id) {
        Pedido p = pedido(id);
        if (!"aguardando".equalsIgnoreCase(p.getStatus()) || p.getProEmail() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido não está mais aberto a propostas.");
        return p;
    }
    private boolean dono(Pedido p, TaskgoPrincipal u) { return p.getClientEmail() != null && p.getClientEmail().equalsIgnoreCase(u.email()); }
    private Long idCliente(Pedido p) { return p.getClientEmail() == null ? null : usuarios.findByEmailIgnoreCase(p.getClientEmail()).map(Usuario::getId).orElse(null); }
    private TaskgoPrincipal principal(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }

    /* Só a proposta do profissional traz os termos; a contraproposta do cliente herda os da última dele. */
    private static void validarTermos(NovaProposta b) {
        if (b.prazoDias() == null || b.prazoDias() < 0 || b.prazoDias() > 90)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o prazo de 0 (hoje) a 90 dias.");
        if (b.garantiaDias() == null || b.garantiaDias() < 0 || b.garantiaDias() > 365)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a garantia de 0 a 365 dias.");
        if (b.materialIncluso() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe se o material está incluso.");
    }

    public record NovaProposta(Double valor, Long profissionalId, Integer prazoDias, Integer garantiaDias, Boolean materialIncluso) {
        public NovaProposta(Double valor, Long profissionalId) { this(valor, profissionalId, null, null, null); }
    }
    /* A proposta leva a nota do profissional: o cliente escolhe sabendo com quem vai lidar. */
    private PropostaDto dto(Proposta x) { return dtoComparavel(x, null, null); }

    /* Cliente dono: distância de cada profissional até o pedido e serviços concluídos, em consultas só para a lista. */
    private List<PropostaDto> comparavel(List<Proposta> lista, Pedido p) {
        if (ranking == null) return lista.stream().map(this::dto).toList();
        Map<Long, Usuario> porId = new HashMap<>();
        usuarios.findAllById(lista.stream().map(Proposta::getProfissionalId).distinct().toList()).forEach(u -> porId.put(u.getId(), u));
        Map<Long, RankingProfissionais.Metricas> metricas = ranking.metricas(porId.values());
        return lista.stream().map(x -> {
            Usuario pro = porId.get(x.getProfissionalId());
            /* Do ponto de partida (nunca da posição ao vivo) e em km inteiros: vários pedidos não triangulam onde o profissional está. */
            Double km = pro == null ? null : AreaAtendimentoController.distancia(AreaAtendimentoController.ponto(pro, null), p);
            RankingProfissionais.Metricas m = metricas.get(x.getProfissionalId());
            return dtoComparavel(x, km == null ? null : (double) Math.max(1, Math.round(km)), m == null ? null : (int) m.concluidos());
        }).toList();
    }

    private PropostaDto dtoComparavel(Proposta x, Double km, Integer concluidos) {
        Object[] r = ProfissionalController.resumo(pedidos.resumoAvaliacoes(x.getProfissionalEmail()));
        Double nota = r[0] instanceof Number n ? Math.round(n.doubleValue() * 10) / 10.0 : null;
        int total = r[1] instanceof Number n ? n.intValue() : 0;
        return new PropostaDto(x.getId(), x.getPedidoId(), x.getProfissionalId(), x.getProfissionalNome(), x.getAutor(), x.getValor(),
                x.getStatus(), x.getCriadaEm(), x.getRespondidaEm(), nota, total, x.getPrazoDias(), x.getGarantiaDias(), x.getMaterialIncluso(), km, concluidos);
    }

    public record PropostaDto(String id, String pedidoId, Long profissionalId, String profissionalNome, String autor, double valor,
                              String status, long criadaEm, Long respondidaEm, Double profissionalNota, int profissionalAvaliacoes,
                              Integer prazoDias, Integer garantiaDias, Boolean materialIncluso, Double km, Integer concluidos) {}
}
