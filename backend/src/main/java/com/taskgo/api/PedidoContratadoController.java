package com.taskgo.api;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos/contratados")
public class PedidoContratadoController {
    private final PedidoService service;
    private final UsuarioRepository usuarios;
    private final LocalizacaoPedidoService localizacoes;
    private final ModeracaoService moderacao;
    private final EventosService eventos;
    private final PedidoContratadoRepository pedidos;
    public PedidoContratadoController(PedidoService service, UsuarioRepository usuarios, LocalizacaoPedidoService localizacoes,
                                      ModeracaoService moderacao, EventosService eventos, PedidoContratadoRepository pedidos) {
        this.service = service; this.usuarios = usuarios; this.localizacoes=localizacoes; this.moderacao=moderacao; this.eventos=eventos; this.pedidos=pedidos;
    }

    private ChamarAgoraService agora;
    private NotificacaoService notificacoes;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAgora(ChamarAgoraService agora) { this.agora = agora; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNotificacoes(NotificacaoService notificacoes) { this.notificacoes = notificacoes; }
    private PagamentoService pagamentos;
    private PresencaProfissionalRepository presencas;
    private FotoPedidoRepository fotosPedido;
    private AgendaService agenda;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setAgendaService(AgendaService agenda) { this.agenda = agenda; }
    /* Plano Pro: selo para o cliente. */
    private PlanoService planos;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPlanos(@org.springframework.context.annotation.Lazy PlanoService p) { this.planos = p; }
    private ExigenciaVerificacao exigencia;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setFotosPedido(FotoPedidoRepository fotosPedido) { this.fotosPedido = fotosPedido; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPresencas(PresencaProfissionalRepository presencas) { this.presencas = presencas; }
    private PublicacaoPedidoService publicacao;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPagamentos(PagamentoService pagamentos) { this.pagamentos = pagamentos; }
    private ServicosProfissional servicosPro;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setServicosPro(ServicosProfissional s) { this.servicosPro = s; }
    private DinheiroService dinheiro;
    private ObraService obras;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setObras(ObraService obras) { this.obras = obras; }
    private RecorrenteService recorrentes;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setRecorrentes(RecorrenteService r) { this.recorrentes = r; }
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setDinheiro(DinheiroService dinheiro) { this.dinheiro = dinheiro; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPublicacao(PublicacaoPedidoService publicacao) { this.publicacao = publicacao; }
    /* Campos do pagamento só o servidor escreve. */
    private static final java.util.Set<String> EXTRAS_DO_SERVIDOR = java.util.Set.of("pagamentoPendente", "repasse", "taxa", "confirmacaoCliente");
    private void avisar(Long usuarioId, String pedidoId, String titulo, String texto, String chave) {
        if (notificacoes == null || usuarioId == null) return;
        try { notificacoes.criar(usuarioId, pedidoId, "pedido", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
    static String nomeServico(String id) { return "ajuda".equals(id) ? "Ajuda rápida" : CatalogoServicos.nome(String.valueOf(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido criar(@RequestBody Pedido pedido, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        exigirTipo(p, "cliente");
        moderacao.exigirLiberado(p.id());
        /* Serviço não pago ou taxa de cancelamento em aberto: bloqueia novos pedidos até pagar. */
        /* Para fazer pedidos, a identidade precisa estar verificada. */
        if (exigencia != null) exigencia.exigirParaPedir(p.id());
        if (dinheiro != null) dinheiro.exigirSemPendencia(p.id());
        pedido.setClientEmail(p.email()); pedido.setClientId(String.valueOf(p.id()));
        if (pedido.getProEmail() != null && !pedido.getProEmail().isBlank()) {
            Usuario pro = usuarios.findByEmailIgnoreCase(pedido.getProEmail().trim())
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Profissional selecionado não encontrado."));
            if (exigencia != null && !exigencia.liberado(pro))
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Este profissional ainda não pode receber pedidos.");
            if (!"profissional".equalsIgnoreCase(pro.getTipo()) || pro.getProId() == null || pro.getProId().isBlank()
                    || pedido.getProId() != null && !pedido.getProId().equals(pro.getProId()))
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Profissional selecionado inválido.");
            pedido.setProEmail(pro.getEmail()); pedido.setProId(pro.getProId()); pedido.setProfessionalName(pro.getNome());
        }
        boolean chamarAgora = "agora".equals(pedido.getExtra().get("modo"));
        if (chamarAgora) {
            if (!(pedido.getExtra().get("lat") instanceof Number) || !(pedido.getExtra().get("lng") instanceof Number))
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Para chamar agora, marque no mapa onde você está.");
            pedido.getExtra().put("urgente", true); pedido.getExtra().put("quando", "agora");
            pedido.getExtra().remove("notificados"); pedido.getExtra().remove("recusados"); pedido.getExtra().remove("onda");
        } else if (!"obra".equals(pedido.getExtra().get("modo"))) pedido.getExtra().put("modo", "agendar");
        EXTRAS_DO_SERVIDOR.forEach(pedido.getExtra()::remove);
        /* Com pagamento ligado, o pedido espera o pagamento antes de procurar profissional (PagamentoService publica). */
        /* Em dinheiro: o pedido sai na hora e o cliente paga a quem atender, no fim do serviço. */
        boolean emDinheiro = DinheiroService.DINHEIRO.equals(pedido.getExtra().get(DinheiroService.FORMA));
        /* Modelo direto: o cliente paga quem atende (Pix na chave dele ou dinheiro); nada é cobrado ao enviar. */
        boolean direto = pagamentos != null && pagamentos.direto();
        if (direto && !DinheiroService.FORMAS_DIRETAS.contains(String.valueOf(pedido.getExtra().get(DinheiroService.FORMA)))) pedido.getExtra().put(DinheiroService.FORMA, DinheiroService.PIX_DIRETO);
        if (!direto && !emDinheiro) pedido.getExtra().remove(DinheiroService.FORMA);
        emDinheiro = emDinheiro || direto;
        /* Obra: os orçamentos chegam antes; cada etapa é paga depois, na obra. */
        boolean obra = "obra".equals(pedido.getExtra().get("modo"));
        /* Recorrente: sai sem cobrança; o cliente paga por semana depois que alguém aceita. */
        boolean recorrente = pedido.getExtra().get("recorrente") != null;
        boolean pagar = pagamentos != null && pagamentos.ativo() && !emDinheiro && !obra && !recorrente;
        Pedido criado = service.criar(pedido, pagar);
        if (recorrentes != null) recorrentes.gerar(criado);
        moderacao.registrar(p.id(), "pedido", "Pediu " + nomeServico(criado.getService()) + (chamarAgora ? " (chamar agora)" : "") + " por " + PagamentoService.reais(java.math.BigDecimal.valueOf(criado.getPrice())), p.id(), criado.getId());
        if (!pagar && publicacao != null) publicacao.publicar(criado);
        return criado;
    }

    @GetMapping
    public List<Pedido> listar(Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        List<Pedido> lista = service.listarDoUsuario(p.email());
        /* Depois do aceite, o profissional vê nome e telefone do cliente; o cliente vê o telefone do profissional. */
        /* Contratação direta ainda sem pagamento não aparece para o profissional. */
        lista = lista.stream().filter(x -> !("aguardando_pagamento".equals(x.getStatus()) && p.email().equalsIgnoreCase(String.valueOf(x.getProEmail())))).collect(java.util.stream.Collectors.toList());
        for (Pedido x : lista) {
            if ("aguardando".equalsIgnoreCase(x.getStatus()) || "aguardando_pagamento".equals(x.getStatus())) continue;
            boolean souPro = p.email().equalsIgnoreCase(String.valueOf(x.getProEmail()));
            boolean esperandoCliente = "pendente".equals(x.getExtra().get("confirmacaoCliente"));
            String outro = souPro ? x.getClientEmail() : x.getProEmail();
            if (outro != null) usuarios.findByEmailIgnoreCase(outro).ifPresent(u -> {
                x.addExtra(souPro ? "clientName" : "proName", u.getNome());
                x.addExtra(souPro ? "clientUid" : "proUid", u.getId());
                x.addExtra(souPro ? "clientTemFoto" : "proTemFoto", FotoController.foto(u) != null);
                x.addExtra(souPro ? "clientVerificado" : "proVerificado", VerificacaoController.verificado(u));
                if (!souPro) x.addExtra("proPlanoPro", planos != null && planos.ehPro(u.getId()));
                /* Telefone e endereço só depois que o cliente confirma quem vai atender. */
                if (!esperandoCliente) x.addExtra(souPro ? "clientPhone" : "proPhone", u.getTelefone());
            });
            if (souPro && esperandoCliente) x.setAddress(null);
        }
        return lista;
    }

    @GetMapping("/disponiveis")
    public ApiPage<OportunidadePedidoDto> disponiveis(@RequestParam(required = false) String servico,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created,desc") String sort, Authentication auth) {
        TaskgoPrincipal p = principal(auth); exigirTipo(p, "profissional");
        moderacao.exigirLiberado(p.id());
        if (exigencia != null) exigencia.exigirParaAtender(p.id());
        Usuario eu = usuarios.findById(p.id()).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!DisponibilidadeController.online(eu))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Você está offline. Fique online para ver as oportunidades.");
        Pageable pageable = PedidoController.pageable(page, size, sort, List.of("created", "price"));
        /* Sem filtro: todas as categorias do profissional. Com filtro: só aquela, se for uma das dele. */
        List<String> minhas = servicosPro == null ? List.of(String.valueOf(eu.getService())) : servicosPro.de(eu);
        List<String> busca = servico != null && !servico.isBlank() && minhas.contains(servico.trim()) ? List.of(servico.trim()) : minhas;
        Page<Pedido> result = service.listarDisponiveis(busca, pageable);
        /* Chamado agora só aparece para quem foi chamado; quem o cliente recusou não vê mais o pedido. */
        /* Só pedidos dentro da área de atendimento (raio a partir de onde ele está ou do ponto de partida). */
        AreaAtendimentoController.Ponto de = AreaAtendimentoController.ponto(eu, presencas);
        List<Pedido> visiveis = result.getContent().stream()
                .filter(x -> !ChamarAgoraService.modoAgora(x) || ChamarAgoraService.convidado(x, p.id()))
                .filter(x -> ChamarAgoraService.modoAgora(x) || AreaAtendimentoController.dentro(eu, de, x))
                .filter(x -> !ChamarAgoraService.ids(x, "recusados").contains(p.id())).toList();
        Page<Pedido> filtrado = new org.springframework.data.domain.PageImpl<>(visiveis, result.getPageable(), result.getTotalElements() - (result.getContent().size() - visiveis.size()));
        return ApiPage.from(filtrado.map(x -> oportunidade(x).comKm(AreaAtendimentoController.distancia(de, x))
                .comFotos(fotosPedido == null ? null : fotosPedido.resumos(x.getId(), "pedido").stream().map(FotoPedidoRepository.Resumo::id).toList())
                .comConflito(agenda == null || x.getSched() == null ? null : agenda.conflito(eu.getId(), eu.getEmail(), x.getSched(), x.getId()))));
    }

    @GetMapping("/{id}")
    public Object obter(@PathVariable String id, Authentication auth) {
        TaskgoPrincipal principal = principal(auth);
        Pedido relacionado = service.listarDoUsuario(principal.email()).stream()
                .filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        if (relacionado != null) return relacionado;
        /* Quem ajuda (não profissional) só vê a ajuda rápida para a qual foi chamado. */
        if (!"profissional".equalsIgnoreCase(principal.tipo()))
            return service.encontrarDisponivel(id).filter(x -> AjudaRapida.eh(x) && ChamarAgoraService.convidado(x, principal.id())).<Object>map(this::oportunidade)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        if (exigencia != null) exigencia.exigirParaAtender(principal.id());
        return service.encontrarDisponivel(id).<Object>map(this::oportunidade)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public Pedido atualizar(@PathVariable String id, @RequestBody Pedido pedido, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        /* Obra: cancelar segue as regras das etapas (aprovada fica, paga e não começada volta, em andamento vai para a equipe). */
        if (obras != null && "cancelado".equals(pedido.getStatus()) && pedidos.findById(id).map(ObraService::eh).orElse(false)) return obras.cancelar(id, p, pedido.getCancelReason());
        /* Recorrente: pagas e não feitas voltam, feitas vão para o profissional. */
        if (recorrentes != null && "cancelado".equals(pedido.getStatus()) && pedidos.findById(id).map(RecorrenteService::eh).orElse(false)) return recorrentes.cancelar(id, p, pedido.getCancelReason());
        /* "Cheguei" do profissional passa pela checagem do GPS contra o ponto marcado pelo cliente. */
        if ("chegou".equals(pedido.getStatus()))
            pedidos.findById(id).filter(a -> "a_caminho".equalsIgnoreCase(a.getStatus()) && p.email().equalsIgnoreCase(String.valueOf(a.getProEmail())))
                    .ifPresent(a -> { String obs = localizacoes.exigirPertoDoEndereco(a); if (obs != null) { moderacao.registrar(p.id(), "pedido", obs, p.id(), a.getId()); pedido.addExtra("chegadaImprecisa", true); } });
        String antes = pedidos.findById(id).map(Pedido::getStatus).orElse(null);
        Pedido atualizado=service.atualizar(id, p.email(), pedido);
        if ("cancelado".equals(atualizado.getStatus()) && !"cancelado".equals(antes))
            moderacao.registrar(p.id(), "cancelamento", "Cancelou o pedido de " + atualizado.getService() + (atualizado.getCancelReason() == null ? "" : " (" + atualizado.getCancelReason() + ")"), p.id(), atualizado.getId());
        if (pagamentos != null && !java.util.Objects.equals(antes, atualizado.getStatus())) {
            if ("concluido".equals(atualizado.getStatus())) pagamentos.aoConcluir(atualizado);
            if ("cancelado".equals(atualizado.getStatus())) pagamentos.estornar(atualizado);
        }
        localizacoes.limparSeEncerrado(atualizado);
        avisarOutroLado(atualizado, p.email());
        avisarStatus(atualizado, p.email(), antes);
        if (!java.util.Objects.equals(antes, atualizado.getStatus()) && java.util.Set.of("chegou", "em_andamento", "concluido").contains(atualizado.getStatus()))
            moderacao.registrar(p.id(), "pedido", java.util.Map.of("chegou", "Marcou a chegada", "em_andamento", "Iniciou o serviço com o código", "concluido", "Marcou o serviço como concluído").get(atualizado.getStatus()), p.id(), id);
        return atualizado;
    }

    @PostMapping("/{id}/aceitar")
    public Pedido aceitar(@PathVariable String id, Authentication auth) {
        TaskgoPrincipal p = principal(auth);
        moderacao.exigirLiberado(p.id());
        Usuario eu = usuarios.findById(p.id()).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Pedido antes = pedidos.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (ObraService.eh(antes)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Obras são contratadas por orçamento: mande o seu na página da obra.");
        boolean ajuda = AjudaRapida.eh(antes);
        if (!ajuda) { exigirTipo(p, "profissional"); if (exigencia != null) exigencia.exigirParaAtender(p.id()); }
        if (ajuda ? !AjudaRapida.ativo(eu) : !DisponibilidadeController.online(eu))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, ajuda ? "Ligue a ajuda rápida para aceitar." : "Você está offline. Fique online para aceitar pedidos.");
        if (ChamarAgoraService.ids(antes, "recusados").contains(p.id()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "O cliente escolheu outro profissional para este pedido.");
        if (ChamarAgoraService.modoAgora(antes) && !ChamarAgoraService.convidado(antes, p.id()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Este chamado não foi enviado para você.");
        boolean recorrente = RecorrenteService.eh(antes) && recorrentes != null;
        if (recorrente) recorrentes.exigirLivre(eu, antes);
        else if (agenda != null && "profissional".equalsIgnoreCase(eu.getTipo())) agenda.exigirLivre(eu, antes);
        if (dinheiro != null) dinheiro.exigirChaveParaAceitar(eu, antes);
        Pedido aceito = service.aceitar(id, p.email());
        if (recorrente) aceito = recorrentes.aoAceitar(aceito, eu);
        moderacao.registrar(p.id(), "pedido", "Aceitou o pedido de " + nomeServico(aceito.getService()), p.id(), id);
        /* O cliente confere quem aceitou e pode recusar antes de liberar endereço e telefone. */
        aceito.getExtra().put("confirmacaoCliente", "pendente");
        aceito = pedidos.save(aceito);
        if (agora != null && ChamarAgoraService.modoAgora(aceito)) agora.encerrarParaOsOutros(aceito, p.id());
        avisarOutroLado(aceito, p.email());
        Long cliente = usuarios.findByEmailIgnoreCase(String.valueOf(aceito.getClientEmail())).map(Usuario::getId).orElse(null);
        avisar(cliente, aceito.getId(), eu.getNome() + " aceitou seu pedido", "Confira o perfil e confirme para liberar o endereço, ou recuse e o pedido volta a procurar.", "aceite:" + aceito.getId() + ":" + p.id());
        return aceito;
    }

    /** O cliente gostou do perfil de quem aceitou: libera endereço e telefone, e o profissional pode sair. */
    @PostMapping("/{id}/confirmar-profissional")
    public Pedido confirmarProfissional(@PathVariable String id, Authentication auth) {
        TaskgoPrincipal p = principal(auth); exigirTipo(p, "cliente");
        Pedido x = pedidos.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!p.email().equalsIgnoreCase(String.valueOf(x.getClientEmail()))) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!"pendente".equals(x.getExtra().get("confirmacaoCliente")) || x.getProEmail() == null)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Não há profissional esperando confirmação neste pedido.");
        x.getExtra().put("confirmacaoCliente", "confirmada");
        Pedido salvo = pedidos.save(x);
        usuarios.findByEmailIgnoreCase(x.getProEmail()).ifPresent(pro -> {
            eventos.enviar(pro.getId(), "pedido", id);
            avisar(pro.getId(), id, "Cliente confirmou: pode ir", "O endereço e o telefone do cliente já aparecem no pedido.", "confirmado:" + id + ":" + pro.getId());
        });
        return salvo;
    }

    /** O cliente não quis quem aceitou: o pedido volta a procurar, sem essa pessoa. */
    @PostMapping("/{id}/recusar-profissional")
    public Pedido recusarProfissional(@PathVariable String id, Authentication auth) {
        TaskgoPrincipal p = principal(auth); exigirTipo(p, "cliente");
        Pedido x = pedidos.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!p.email().equalsIgnoreCase(String.valueOf(x.getClientEmail()))) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!"pendente".equals(x.getExtra().get("confirmacaoCliente")) || x.getProEmail() == null)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Só dá para recusar antes de confirmar o profissional.");
        String proEmail = x.getProEmail();
        Usuario pro = usuarios.findByEmailIgnoreCase(proEmail).orElse(null);
        if (RecorrenteService.eh(x) && recorrentes != null ? !recorrentes.devolverAoMural(id, proEmail) : pedidos.devolverAoMural(id, proEmail) == 0)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "O pedido mudou. Atualize a página.");
        Pedido aberto = pedidos.findById(id).orElseThrow();
        if (pro != null) {
            if (agora != null) agora.reabrir(aberto, pro.getId());
            eventos.enviar(pro.getId(), "pedido", id);
            avisar(pro.getId(), id, "O cliente escolheu outro profissional", "O pedido de " + nomeServico(aberto.getService()).toLowerCase(java.util.Locale.ROOT) + " não está mais com você. Nenhuma punição: acontece.", "recusado:" + id + ":" + pro.getId());
        }
        if (!ChamarAgoraService.modoAgora(aberto))
            usuarios.profissionaisDoServico(aberto.getService()).stream()
                    .filter(DisponibilidadeController::online).filter(u -> pro == null || !u.getId().equals(pro.getId()))
                    .forEach(u -> eventos.enviar(u.getId(), "oportunidade", id));
        return pedidos.findById(id).orElseThrow();
    }

    @PostMapping("/{id}/avaliacoes")
    public Pedido avaliar(@PathVariable String id, @RequestBody AvaliacaoRequest request, Authentication auth) {
        TaskgoPrincipal p=principal(auth);
        if(!"cliente".equalsIgnoreCase(p.tipo())&& !"profissional".equalsIgnoreCase(p.tipo())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return service.avaliar(id,p.email(),request==null?null:request.rating(),request==null?null:request.comment());
    }

    /* Oportunidade com a nota do cliente: o profissional decide sabendo com quem vai lidar. */
    private OportunidadePedidoDto oportunidade(Pedido pedido) {
        Usuario cliente = pedido.getClientEmail() == null ? null : usuarios.findByEmailIgnoreCase(pedido.getClientEmail()).orElse(null);
        double soma = 0; int total = 0;
        if (pedido.getClientEmail() != null) for (Pedido x : pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(pedido.getClientEmail(), "")) {
            if (x.getExtra().get("clientRating") instanceof Number n && pedido.getClientEmail().equalsIgnoreCase(x.getClientEmail())) { soma += n.doubleValue(); total++; }
        }
        String primeiroNome = cliente == null || cliente.getNome() == null ? null : cliente.getNome().trim().split("\\s+")[0];
        return OportunidadePedidoDto.from(pedido, primeiroNome, total == 0 ? null : Math.round(soma / total * 10) / 10.0, total,
                cliente == null ? null : cliente.getId(), FotoController.foto(cliente) != null);
    }

    private void avisarOutroLado(Pedido pedido, String quem) {
        String outro = quem.equalsIgnoreCase(String.valueOf(pedido.getClientEmail())) ? pedido.getProEmail() : pedido.getClientEmail();
        if (outro != null) usuarios.findByEmailIgnoreCase(outro).ifPresent(u -> eventos.enviar(u.getId(), "pedido", pedido.getId()));
    }
    private static final java.util.Map<String, String[]> AVISO_STATUS = java.util.Map.of(
            "chegou", new String[]{"O profissional chegou", "Passe o código de 4 números para ele começar."},
            "em_andamento", new String[]{"Serviço começou", "O atendimento está em andamento."},
            "concluido", new String[]{"Serviço concluído", "Conte como foi: avalie o atendimento."},
            "cancelado", new String[]{"Pedido cancelado", "O pedido foi cancelado pela outra pessoa."});
    private void avisarStatus(Pedido pedido, String quem, String antes) {
        String st = pedido.getStatus();
        if (st == null || st.equals(antes) || !AVISO_STATUS.containsKey(st)) return;
        String outro = quem.equalsIgnoreCase(String.valueOf(pedido.getClientEmail())) ? pedido.getProEmail() : pedido.getClientEmail();
        if (outro == null) return;
        String[] t = AVISO_STATUS.get(st);
        usuarios.findByEmailIgnoreCase(outro).ifPresent(u -> avisar(u.getId(), pedido.getId(), t[0] + " · " + nomeServico(pedido.getService()), t[1], "status:" + pedido.getId() + ":" + st));
    }

    private TaskgoPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return p;
    }
    private void exigirTipo(TaskgoPrincipal p, String tipo) {
        if (!tipo.equalsIgnoreCase(p.tipo())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Operação não permitida para este tipo de conta.");
    }
    public record AvaliacaoRequest(Double rating,String comment) {}
}
