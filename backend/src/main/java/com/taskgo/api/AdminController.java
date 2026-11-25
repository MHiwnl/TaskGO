package com.taskgo.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Fichas de uma conta e de um pedido para a equipe. Nada aqui lê a base inteira:
   a conta vem pelo id, os pedidos dela são os 50 mais recentes e cada ficha aberta fica registrada na auditoria (LGPD).
   Números, filas, busca e auditoria ficam em PainelAdminController. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ModeracaoService moderacao;
    private final UsuarioRepository usuarios;
    private final PedidoContratadoRepository pedidos;
    private final PropostaRepository propostas;
    private final RecursoRepository recursos;
    private final MensagemRepository mensagens;

    public AdminController(ModeracaoService moderacao, UsuarioRepository usuarios, PedidoContratadoRepository pedidos,
                           PropostaRepository propostas, RecursoRepository recursos, MensagemRepository mensagens) {
        this.moderacao = moderacao; this.usuarios = usuarios; this.pedidos = pedidos; this.propostas = propostas;
        this.recursos = recursos; this.mensagens = mensagens;
    }

    private HistoricoContaRepository historico;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setHistorico(HistoricoContaRepository historico) { this.historico = historico; }
    private DenunciaRepository denuncias;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDenuncias(DenunciaRepository denuncias) { this.denuncias = denuncias; }

    @GetMapping("/usuarios/{id}")
    @Transactional
    public Map<String, Object> detalheUsuario(@PathVariable Long id, Authentication auth) {
        TaskgoPrincipal quem = PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.BUSCAR);
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
        ModeracaoService.Situacao s = moderacao.situacao(u);
        String email = String.valueOf(u.getEmail());
        List<Map<String, Object>> peds = pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(email, email, PageRequest.of(0, 50)).stream()
                .map(p -> mapa("id", p.getId(), "servico", p.getService(), "status", status(p), "valor", p.getPrice(), "criadoEm", p.getCreated(),
                        "papel", email.equalsIgnoreCase(String.valueOf(p.getClientEmail())) ? "cliente" : "profissional")).toList();
        Object[] nota = ProfissionalController.resumo(pedidos.resumoAvaliacoes(email));
        /* Quem da equipe abriu a ficha de quem, e quando. */
        if (!quem.id().equals(u.getId())) moderacao.registrar(u.getId(), "acesso", quem.nome() + " (" + PapelEquipe.papelDe(quem, moderacao) + ") abriu a ficha da conta", quem.id(), null);
        return mapa("id", u.getId(), "nome", u.getNome(), "email", u.getEmail(), "tipo", u.getTipo(), "telefone", u.getTelefone(), "cidade", u.getCidade(),
                "cpf", u.getCpf(), "servico", u.getService(), "criadoEm", u.getCriadoEm(), "emailConfirmado", ConfirmacaoEmailService.confirmado(u),
                "verificado", VerificacaoController.verificado(u),
                "online", "profissional".equalsIgnoreCase(u.getTipo()) ? DisponibilidadeController.online(u) : null,
                "avisos", s.avisos(), "suspensoAte", s.suspensoAte(), "suspenso", s.suspenso(), "banido", s.banido(),
                "notaMedia", nota[0] instanceof Number n ? arred(n.doubleValue()) : null, "avaliacoes", nota[1] instanceof Number n ? n.intValue() : 0,
                "denunciasContra", denuncias == null ? 0 : denuncias.countByDenunciadoId(u.getId()),
                "totalPedidos", pedidos.countByClientEmailIgnoreCaseOrProEmailIgnoreCase(email, email),
                "pedidos", peds,
                "historico", historico == null ? List.of() : historico.findByUsuarioIdOrderByCriadoEmDesc(u.getId()).stream().limit(100)
                        .map(h -> mapa("tipo", h.getTipo(), "texto", h.getTexto(), "quando", h.getCriadoEm(), "pedidoId", h.getPedidoId())).toList());
    }

    @GetMapping("/pedidos/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> detalhePedido(@PathVariable String id, Authentication auth) {
        PapelEquipe.exigir(auth, moderacao, PapelEquipe.Area.BUSCAR);
        Pedido p = pedidos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        Function<String, Map<String, Object>> pessoa = e -> e == null ? null : usuarios.findByEmailIgnoreCase(e)
                .map(u -> mapa("id", u.getId(), "nome", u.getNome(), "email", u.getEmail())).orElse(null);
        List<Map<String, Object>> etapas = new ArrayList<>();
        if (p.getCreated() != null) etapas.add(mapa("texto", "Pedido criado", "quando", p.getCreated()));
        if (p.getAcceptedAt() != null) etapas.add(mapa("texto", "Aceito pelo profissional", "quando", p.getAcceptedAt()));
        if (p.getCompletedAt() != null) etapas.add(mapa("texto", "Serviço concluído", "quando", p.getCompletedAt()));
        if ("cancelado".equals(status(p))) etapas.add(mapa("texto", "Cancelado" + (p.getCancelReason() == null ? "" : ": " + p.getCancelReason()), "quando", null));
        return mapa("id", p.getId(), "servico", p.getService(), "status", status(p), "valor", p.getPrice(), "urgente", urgente(p),
                "endereco", p.getAddress(), "bairro", p.getExtra().get("bairro"), "descricao", p.getNotes(), "nota", p.getRating(),
                "pagamento", p.getPaid(), "liberacao", p.getExtra().get("liberacao"), "problema", p.getExtra().get("problema"),
                "cliente", pessoa.apply(p.getClientEmail()), "profissional", pessoa.apply(p.getProEmail()), "etapas", etapas,
                "propostas", propostas.findByPedidoIdOrderByCriadaEmAsc(p.getId()).stream().map(x -> mapa("autor", x.getAutor(), "profissional", x.getProfissionalNome(),
                        "valor", x.getValor(), "status", x.getStatus(), "quando", x.getCriadaEm())).toList(),
                "mensagens", mensagens.findByPedidoIdOrderByCriadaEmAsc(p.getId()).size(),
                "denuncias", denuncias == null ? 0 : denuncias.countByPedidoId(p.getId()));
    }

    /* Conversa de um pedido: só com denúncia ou recurso em análise (Marco Civil art. 8º). Não marca nada como lido. */
    @GetMapping("/pedidos/{id}/mensagens")
    /* Não pode ser readOnly: o registro de "acesso" grava no histórico e, em transação só de leitura, o Hibernate não grava. */
    @Transactional
    public List<MensagemController.MensagemDto> conversaDoPedido(@PathVariable String id, Authentication auth) {
        Pedido p = pedidos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        moderacao.exigirAcessoConversa(auth, id, idDe(p.getClientEmail()), idDe(p.getProEmail()));
        return mensagens.findByPedidoIdOrderByCriadaEmAsc(id).stream().map(MensagemController.MensagemDto::from).toList();
    }
    private Long idDe(String email) { return email == null ? null : usuarios.findByEmailIgnoreCase(email).map(Usuario::getId).orElse(null); }

    /* ---------- utilidades ---------- */
    static boolean urgente(Pedido p) { return Boolean.TRUE.equals(p.getExtra().get("urgente")); }
    static String status(Pedido p) { return p.getStatus() == null ? "" : p.getStatus().toLowerCase(Locale.ROOT); }
    static double arred(double v) { return Math.round(v * 10) / 10.0; }
    /* Map.of não aceita null; este aceita e mantém a ordem. */
    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
