package com.taskgo.api;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* LGPD: a pessoa baixa tudo o que a TaskGO guarda sobre ela, e o que já passou do prazo é apagado sozinho.
   Prazos (dias) configuráveis: conversas de pedidos encerrados, notificações lidas e histórico do log. */
@RestController
public class PrivacidadeController {
    private static final Logger log = LoggerFactory.getLogger(PrivacidadeController.class);
    private final UsuarioRepository usuarios;
    private final PedidoContratadoRepository pedidos;
    private final MensagemRepository mensagens;
    private final NotificacaoRepository notificacoes;
    private final PropostaRepository propostas;
    private final RecursoRepository recursos;
    private final HistoricoContaRepository historico;
    private final int diasConversas, diasNotificacoes, diasHistorico;
    private DenunciaRepository denuncias;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDenuncias(DenunciaRepository denuncias) { this.denuncias = denuncias; }
    private SessaoContaRepository sessoes;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSessoes(SessaoContaRepository sessoes) { this.sessoes = sessoes; }

    public PrivacidadeController(UsuarioRepository usuarios, PedidoContratadoRepository pedidos, MensagemRepository mensagens,
                                 NotificacaoRepository notificacoes, PropostaRepository propostas, RecursoRepository recursos,
                                 HistoricoContaRepository historico,
                                 @Value("${taskgo.retencao.conversas-dias:180}") int diasConversas,
                                 @Value("${taskgo.retencao.notificacoes-dias:365}") int diasNotificacoes,
                                 @Value("${taskgo.retencao.historico-dias:730}") int diasHistorico) {
        this.usuarios = usuarios; this.pedidos = pedidos; this.mensagens = mensagens; this.notificacoes = notificacoes;
        this.propostas = propostas; this.recursos = recursos; this.historico = historico;
        this.diasConversas = diasConversas; this.diasNotificacoes = diasNotificacoes; this.diasHistorico = diasHistorico;
    }

    /* Prazos publicados: a política de privacidade lê daqui, assim o texto nunca fica diferente do que o sistema faz. */
    @GetMapping("/api/privacidade/prazos")
    public Map<String, Integer> prazos() {
        return Map.of("conversasDias", diasConversas, "notificacoesDias", diasNotificacoes, "historicoDias", diasHistorico);
    }

    @GetMapping("/api/conta/meus-dados")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> meusDados(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String email = u.getEmail().toLowerCase(Locale.ROOT);
        List<Pedido> meus = pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(email, email);
        Set<String> ids = new java.util.HashSet<>(meus.stream().map(Pedido::getId).toList());
        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("id", u.getId()); conta.put("nome", u.getNome()); conta.put("email", u.getEmail()); conta.put("tipo", u.getTipo());
        conta.put("cpf", u.getCpf()); conta.put("telefone", u.getTelefone()); conta.put("cidade", u.getCidade()); conta.put("endereco", u.getEndereco());
        conta.put("bio", u.getBio()); conta.put("nascimento", u.getBirth()); conta.put("servico", u.getService()); conta.put("criadaEm", u.getCriadoEm());
        /* Códigos e marcas internas de segurança não saem; a situação da conta sai em palavras. */
        Map<String, Object> extras = new LinkedHashMap<>(u.getExtra());
        extras.keySet().removeIf(k -> k.startsWith("emailCodigo") || k.startsWith("seguranca"));
        conta.put("verificacaoEmDuasEtapas", SegurancaContaController.doisFatoresAtivo(u));
        if (sessoes != null) conta.put("sessoesAbertas", sessoes.findByUsuarioIdAndEncerradaEmIsNullOrderByVistaEmDesc(u.getId()).stream()
                .map(x -> Map.of("dispositivo", String.valueOf(x.getDispositivo()), "ip", String.valueOf(x.getIp()), "criadaEm", x.getCriadaEm(), "vistaEm", x.getVistaEm())).toList());
        conta.put("outrosDados", extras);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("geradoEm", System.currentTimeMillis());
        dados.put("aviso", "Cópia dos dados que a TaskGO guarda sobre esta conta (LGPD, art. 18). A senha não é guardada em texto e por isso não aparece.");
        dados.put("conta", conta);
        /* O código de chegada é só do cliente; para o profissional ele não sai nem aqui. */
        dados.put("pedidos", meus.stream().map(x -> { Map<String, Object> m = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(x, Map.class);
            if (!email.equalsIgnoreCase(String.valueOf(x.getClientEmail()))) m.remove("code"); return m; }).toList());
        dados.put("propostas", ids.isEmpty() ? List.of() : propostas.findByPedidoIdIn(ids));
        dados.put("mensagens", mensagens.findByAutorIdOrDestinatarioIdOrderByCriadaEmAsc(p.id(), p.id()).stream()
                .map(m -> Map.of("pedidoId", m.getPedidoId(), "enviadaPorVoce", p.id().equals(m.getAutorId()), "texto", m.getTexto(), "criadaEm", m.getCriadaEm())).toList());
        dados.put("notificacoes", notificacoes.findByUsuarioIdOrderByCriadaEmDesc(p.id()).stream()
                .map(n -> Map.of("titulo", n.getTitulo(), "conteudo", n.getConteudo(), "criadaEm", n.getCriadaEm())).toList());
        dados.put("recursos", recursos.findByUsuarioIdOrderByCriadoEmDesc(p.id()));
        dados.put("historico", historico.findByUsuarioIdOrderByCriadoEmDesc(p.id()).stream()
                .map(h -> Map.of("tipo", h.getTipo(), "texto", h.getTexto(), "criadoEm", h.getCriadoEm())).toList());
        if (denuncias != null) dados.put("denunciasFeitas", denuncias.findByDenuncianteIdOrderByCriadaEmDesc(p.id()).stream()
                .map(d -> Map.of("pedidoId", String.valueOf(d.getPedidoId()), "motivo", d.getMotivo(), "status", d.getStatus(), "criadaEm", d.getCriadaEm())).toList());
        dados.put("prazosDeGuarda", prazos());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"taskgo-meus-dados.json\"").body(dados);
    }

    /* Todo dia às 3h: apaga o que passou do prazo. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Recife")
    @Transactional
    public void aplicarPrazos() {
        long agora = System.currentTimeMillis(), dia = Duration.ofDays(1).toMillis();
        /* Tudo filtrado no banco: o custo não cresce com o histórico inteiro. */
        List<String> encerradosHaTempo = pedidos.encerradosAntesDe(agora - diasConversas * dia);
        long conversas = 0;
        if (!encerradosHaTempo.isEmpty()) { conversas = mensagens.countByPedidoIdIn(encerradosHaTempo); mensagens.deleteByPedidoIdIn(encerradosHaTempo); }
        long avisos = notificacoes.apagarLidasAntesDe(agora - diasNotificacoes * dia);
        long fatos = historico.apagarAntesDe(agora - diasHistorico * dia);
        if (conversas + avisos + fatos > 0) log.info("Prazos de guarda: {} mensagens, {} notificações e {} registros do histórico apagados.", conversas, avisos, fatos);
    }

    /* Exclusão de conta: apaga o que o resto da exclusão não alcança. */
    @Transactional
    public void apagarRestos(Long usuarioId) {
        historico.deleteByUsuarioId(usuarioId);
        if (denuncias != null) denuncias.deleteByDenuncianteIdOrDenunciadoId(usuarioId, usuarioId);
        if (sessoes != null) sessoes.deleteByUsuarioId(usuarioId);
        recursos.deleteAll(recursos.findByUsuarioIdOrderByCriadoEmDesc(usuarioId));
        propostas.deleteByProfissionalId(usuarioId);
    }
}
