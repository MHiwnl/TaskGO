package com.taskgo.api;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* Moderação da conversa e situação da conta.
   Números, valores e contatos não podem ir pelo chat: protege os dois lados e evita o serviço por fora.
   1º aviso; 2º suspensão de 24 horas; 3º banimento. Toda decisão pode ser contestada por recurso. */
@Service
public class ModeracaoService {
    public static final long SUSPENSAO_MS = 24L * 60 * 60 * 1000;
    static final String AVISOS = "moderacaoAvisos", SUSPENSO_ATE = "moderacaoSuspensoAte", BANIDO = "moderacaoBanido";

    private static final Pattern DIGITO = Pattern.compile("\\d");
    private static final Pattern VALOR = Pattern.compile("r\\$|\\breais\\b|\\bconto[s]?\\b|\\bpila[s]?\\b");
    private static final Pattern PALAVRA = Pattern.compile("[a-z]+");
    private static final Set<String> NUMEROS_POR_EXTENSO = Set.of("zero", "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "sete",
            "oito", "nove", "dez", "onze", "doze", "treze", "catorze", "quatorze", "quinze", "vinte", "trinta", "quarenta", "cinquenta",
            "sessenta", "setenta", "oitenta", "noventa", "cem", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "mil");
    private static final List<String> CONTATO = List.of("whatsapp", "whats", "zap", "wpp", "telefone", "celular", "cpf", "pix", "instagram",
            "insta", "e-mail", "email", "@", ".com", "por fora", "fora do app", "fora do aplicativo", "meu numero", "me liga");

    private final UsuarioRepository usuarios;
    private final Set<String> administradores;

    /* Histórico com data; opcional para os testes que montam o serviço à mão. */
    private HistoricoContaRepository historico;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setHistorico(HistoricoContaRepository historico) { this.historico = historico; }
    /* Avisos da moderação também chegam em Notificações > Conta. */
    private NotificacaoService notificacoes;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNotificacoes(NotificacaoService notificacoes) { this.notificacoes = notificacoes; }
    private void avisarConta(Long usuarioId, String titulo, String texto) {
        if (notificacoes == null || usuarioId == null) return;
        try { notificacoes.criar(usuarioId, null, "conta", titulo, texto, "conta:" + java.util.UUID.randomUUID()); } catch (RuntimeException ignorar) { }
    }
    public void registrar(Long usuarioId, String tipo, String texto, Long autorId, String pedidoId) {
        if (historico != null && usuarioId != null) historico.save(new HistoricoConta(usuarioId, tipo, texto, autorId, pedidoId, System.currentTimeMillis()));
    }

    public ModeracaoService(UsuarioRepository usuarios, @Value("${taskgo.admin.emails:}") String admins) {
        this.usuarios = usuarios;
        this.administradores = java.util.Arrays.stream(admins.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /* Devolve o motivo do bloqueio, ou null quando a mensagem pode ir. */
    public String motivoBloqueio(String texto) {
        if (texto == null) return null;
        String t = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (DIGITO.matcher(t).find()) return "números";
        if (VALOR.matcher(t).find()) return "valores";
        long porExtenso = PALAVRA.matcher(t).results().map(m -> m.group()).filter(NUMEROS_POR_EXTENSO::contains).count();
        if (porExtenso >= 2) return "números escritos por extenso";
        for (String c : CONTATO) if (t.contains(c)) return "contato fora do app";
        return null;
    }

    public boolean administrador(String email) { return email != null && !email.isBlank() && administradores.contains(email.trim().toLowerCase(Locale.ROOT)); }

    public Situacao situacao(Usuario u) {
        Map<String, Object> e = u.getExtra();
        int avisos = e.get(AVISOS) instanceof Number n ? n.intValue() : 0;
        long ate = e.get(SUSPENSO_ATE) instanceof Number n ? n.longValue() : 0L;
        boolean banido = Boolean.TRUE.equals(e.get(BANIDO));
        boolean suspenso = !banido && ate > System.currentTimeMillis();
        return new Situacao(avisos, suspenso ? ate : null, banido, suspenso);
    }

    /* Registra a infração e aplica a punição da vez. Devolve a nova situação. */
    /* Transação própria: a mensagem barrada volta atrás, mas o aviso fica registrado. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Situacao registrarInfracao(Long usuarioId) { return registrarInfracao(usuarioId, null, null); }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Situacao registrarInfracao(Long usuarioId, String motivo, String pedidoId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        int avisos = situacao(u).avisos() + 1;
        u.getExtra().put(AVISOS, avisos);
        if (avisos == 2) u.getExtra().put(SUSPENSO_ATE, System.currentTimeMillis() + SUSPENSAO_MS);
        if (avisos >= 3) u.getExtra().put(BANIDO, true);
        usuarios.save(u);
        String porque = motivo == null ? "" : " (mensagem com " + motivo + ")";
        registrar(usuarioId, "aviso", "Recebeu o aviso " + avisos + " de 3" + porque, null, pedidoId);
        if (avisos == 2) registrar(usuarioId, "suspensao", "Conta suspensa por 24 horas", null, pedidoId);
        if (avisos >= 3) registrar(usuarioId, "banimento", "Conta banida automaticamente no 3º aviso", null, pedidoId);
        avisarConta(usuarioId, avisos >= 3 ? "Conta banida" : avisos == 2 ? "Conta suspensa por 24 horas" : "Você recebeu um aviso",
                (avisos >= 3 ? "Foi o 3º aviso." : "Aviso " + avisos + " de 3.") + " Combinar contato ou pagamento fora da TaskGO não é permitido. Você pode pedir revisão em Configurações > Situação da conta.");
        return situacao(u);
    }

    /* Recurso aprovado: retira um aviso e a punição que ele trouxe. */
    @Transactional
    public Situacao perdoar(Long usuarioId) { return perdoar(usuarioId, null); }
    @Transactional
    public Situacao perdoar(Long usuarioId, Long autorId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int avisos = Math.max(0, situacao(u).avisos() - 1);
        u.getExtra().put(AVISOS, avisos);
        u.getExtra().remove(SUSPENSO_ATE);
        if (avisos < 3) u.getExtra().remove(BANIDO);
        usuarios.save(u);
        registrar(usuarioId, "recurso", "Recurso aprovado: um aviso retirado (agora " + avisos + " de 3)", autorId, null);
        avisarConta(usuarioId, "Um aviso foi retirado", "Agora você tem " + avisos + " de 3 avisos.");
        return situacao(u);
    }

    @Transactional
    public Situacao banir(Long usuarioId, boolean banir) { return banir(usuarioId, banir, null); }
    @Transactional
    public Situacao banir(Long usuarioId, boolean banir, Long autorId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        registrar(usuarioId, banir ? "banimento" : "desbanimento", banir ? "Conta banida pelo administrador" : "Conta desbanida pelo administrador", autorId, null);
        if (banir) u.getExtra().put(BANIDO, true); else { u.getExtra().remove(BANIDO); u.getExtra().remove(SUSPENSO_ATE); u.getExtra().put(AVISOS, Math.min(situacao(u).avisos(), 2)); }
        usuarios.save(u);
        avisarConta(usuarioId, banir ? "Conta banida pela administração" : "Conta liberada", banir ? "Se achar que foi um engano, peça revisão em Configurações." : "Você já pode usar a TaskGO normalmente.");
        return situacao(u);
    }

    /* Barra quem está suspenso ou banido de pedidos, propostas e conversa. */
    public void exigirLiberado(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Situacao s = situacao(u);
        if (s.banido()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta banida por contato fora do app. Você pode recorrer da decisão.");
        if (s.suspenso()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta suspensa por 24 horas por contato fora do app. Você pode recorrer da decisão.");
    }

    public record Situacao(int avisos, Long suspensoAte, boolean banido, boolean suspenso) {}
}
