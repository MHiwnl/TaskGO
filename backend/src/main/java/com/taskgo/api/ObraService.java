package com.taskgo.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Obras: serviços de vários dias e de alto valor.
 * 1. O cliente descreve a obra; profissionais mandam orçamentos com etapas (ou propõem uma visita técnica paga, abatida se contratados).
 * 2. O cliente paga cada etapa antes de ela começar; a TaskGO segura e libera quando ele aprova (ou em 24 h). Material incluso: até 30% da
 *    etapa sai no início, com foto da nota.
 * 3. A obra ocupa a agenda nos dias combinados; o profissional marca chegada e saída num diário com fotos; atrasos viram aditivos.
 * 4. Faixas: taxa decrescente, mínimo de etapas, reputação exigida, contrato a partir de R$ 5.000, revisão da equipe a partir de R$ 10.000
 *    e garantia de 8% retida por 7 dias a partir de R$ 3.000.
 * 5. Problema numa etapa segura só ela; cancelamento no meio devolve o pago e não começado, e o que estava em andamento vai para a equipe.
 */
@Service
public class ObraService {
    /* Plano Pro: selo para o cliente. */
    private PlanoService planos;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPlanos(@org.springframework.context.annotation.Lazy PlanoService p) { this.planos = p; }
    static final ZoneId FUSO = ZoneId.of("America/Recife");
    static final long DIA = 24L * 60 * 60 * 1000, PRAZO_APROVAR = DIA, PRAZO_GARANTIA = 7 * DIA;
    static final BigDecimal TAXA_VISITA = new BigDecimal("0.15"), VISITA_MIN = new BigDecimal("50"), VISITA_MAX = new BigDecimal("100");
    static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).withZone(FUSO);

    private final OrcamentoRepository orcamentos;
    private final VisitaTecnicaRepository visitas;
    private final EtapaObraRepository etapas;
    private final DiarioObraRepository diario;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final CarteiraRepository carteiras;
    private final TransacaoFinanceiraRepository transacoes;
    private final BloqueioAgendaRepository bloqueios;
    private final PagamentoService pagamentos;
    private final FotoPedidoService fotos;
    private final EventosService eventos;
    private NotificacaoService notificacoes;
    private ModeracaoService moderacao;
    private ExigenciaVerificacao exigencia;
    private DinheiroService dinheiro;
    /* Etapa ou visita sendo paga agora: liga a cobrança criada ao alvo antes de o pagamento imediato (cartão, carteira) voltar. */
    private final ThreadLocal<String> alvo = new ThreadLocal<>();

    public ObraService(OrcamentoRepository orcamentos, VisitaTecnicaRepository visitas, EtapaObraRepository etapas, DiarioObraRepository diario,
                       PedidoContratadoRepository pedidos, UsuarioRepository usuarios, CarteiraRepository carteiras, TransacaoFinanceiraRepository transacoes,
                       BloqueioAgendaRepository bloqueios, PagamentoService pagamentos, FotoPedidoService fotos, EventosService eventos) {
        this.orcamentos = orcamentos; this.visitas = visitas; this.etapas = etapas; this.diario = diario; this.pedidos = pedidos; this.usuarios = usuarios;
        this.carteiras = carteiras; this.transacoes = transacoes; this.bloqueios = bloqueios; this.pagamentos = pagamentos; this.fotos = fotos; this.eventos = eventos;
    }
    @Autowired(required = false) void setNotificacoes(NotificacaoService n) { this.notificacoes = n; }
    @Autowired(required = false) void setModeracao(ModeracaoService m) { this.moderacao = m; }
    @Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }
    @Autowired(required = false) void setDinheiro(DinheiroService d) { this.dinheiro = d; }
    private ServicosProfissional servicosPro;
    @Autowired(required = false) void setServicosPro(ServicosProfissional s) { this.servicosPro = s; }

    public static boolean eh(Pedido p) { return p != null && Boolean.TRUE.equals(p.getExtra().get("obra")); }

    /* ======================= leitura ======================= */

    @Transactional(readOnly = true)
    public Map<String, Object> ver(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = obra(pedidoId);
        boolean cliente = ehCliente(p, quem), atende = ehAtendente(p, quem), equipe = PapelEquipe.equipe(quem.tipo());
        boolean pro = "profissional".equalsIgnoreCase(quem.tipo());
        if (!cliente && !atende && !equipe && !(pro && aberta(p))) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        Map<String, Object> r = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice()).setScale(2, RoundingMode.HALF_UP);
        r.put("aberta", aberta(p));
        r.put("teto", p.getExtra().get("obraTeto"));
        r.put("inicioDesejado", p.getExtra().get("inicioDesejado"));
        r.put("orcamentos", orcamentos.findByPedidoIdOrderByCriadoEmAsc(pedidoId).stream()
                .filter(o -> cliente || equipe || o.getProfissionalId().equals(quem.id()))
                .filter(o -> !cliente || !Orcamento.RETIRADO.equals(o.getStatus()))
                .map(this::orcamentoDto).toList());
        r.put("visitas", visitas.findByPedidoIdOrderByCriadaEmAsc(pedidoId).stream()
                .filter(v -> cliente || equipe || v.getProfissionalId().equals(quem.id())).map(this::visitaDto).toList());
        if (!aberta(p) && (cliente || atende || equipe)) {
            r.put("etapas", etapas.findByPedidoIdOrderByOrdemAsc(pedidoId).stream().map(this::etapaDto).toList());
            r.put("diario", diario.findByPedidoIdOrderByChegadaEmDesc(pedidoId).stream().limit(120).map(this::diarioDto).toList());
            r.put("aditivos", aditivos(p));
            r.put("garantia", p.getExtra().get("obraGarantia"));
            r.put("revisao", p.getExtra().get("obraRevisao"));
            r.put("orcamentoAceito", p.getExtra().get("obraOrcamento"));
        }
        r.put("faixa", faixa(total));
        r.put("jurosMes", pagamentos.jurosMes());
        r.put("direto", pagamentos.direto());
        if (pagamentos.direto() && cliente) r.put("aPagar", temAPagar(pedidoId));
        return r;
    }

    Map<String, Object> faixa(BigDecimal total) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("taxa", FaixaObra.taxa(total)); f.put("minimoEtapas", FaixaObra.minimoEtapas(total)); f.put("maximoEtapas", FaixaObra.maximoEtapas(total));
        f.put("contrato", FaixaObra.contrato(total)); f.put("revisao", FaixaObra.revisaoEquipe(total)); f.put("garantia", FaixaObra.garantia(total));
        return f;
    }

    /* ======================= orçamento ======================= */

    public record EtapaNova(String nome, BigDecimal valor, Integer dias, String entrega) {}
    public record NovoOrcamento(List<EtapaNova> etapas, String material, String listaMaterial, String inicio, List<Integer> diasTrabalho,
                                String horario, String observacao, Boolean contratoAceito) {}

    @Transactional
    public Map<String, Object> enviarOrcamento(String pedidoId, TaskgoPrincipal quem, NovoOrcamento b) {
        Usuario pro = profissional(quem);
        Pedido p = obra(pedidoId);
        if (!aberta(p)) throw erro(HttpStatus.CONFLICT, "Esta obra já escolheu um profissional.");
        if (!(servicosPro != null ? servicosPro.faz(pro, p.getService()) : p.getService().equalsIgnoreCase(String.valueOf(pro.getService())))) throw erro(HttpStatus.FORBIDDEN, "Esta obra é de outra categoria.");
        if (b == null || b.etapas() == null || b.etapas().isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Monte pelo menos uma etapa.");
        if (b.etapas().size() > 12) throw erro(HttpStatus.BAD_REQUEST, "No máximo 12 etapas.");
        BigDecimal total = BigDecimal.ZERO; int diasTotal = 0;
        List<Map<String, Object>> lista = new ArrayList<>();
        for (EtapaNova e : b.etapas()) {
            String nome = e.nome() == null ? "" : e.nome().trim();
            if (nome.length() < 2 || nome.length() > 120) throw erro(HttpStatus.BAD_REQUEST, "Dê um nome a cada etapa (até 120 letras).");
            if (e.valor() == null || e.valor().signum() <= 0 || e.valor().scale() > 2 && e.valor().stripTrailingZeros().scale() > 2) throw erro(HttpStatus.BAD_REQUEST, "Cada etapa precisa de um valor, com até 2 casas decimais.");
            if (e.dias() == null || e.dias() < 1 || e.dias() > 60) throw erro(HttpStatus.BAD_REQUEST, "Cada etapa leva de 1 a 60 dias.");
            String entrega = e.entrega() == null ? "" : e.entrega().trim();
            if (entrega.length() > 500) throw erro(HttpStatus.BAD_REQUEST, "Descreva a entrega da etapa em até 500 letras.");
            BigDecimal v = e.valor().setScale(2, RoundingMode.HALF_UP);
            total = total.add(v); diasTotal += e.dias();
            Map<String, Object> m = new LinkedHashMap<>(); m.put("nome", nome); m.put("valor", v); m.put("dias", e.dias()); m.put("entrega", entrega);
            lista.add(m);
        }
        if (total.compareTo(BigDecimal.valueOf(100_000)) > 0) throw erro(HttpStatus.BAD_REQUEST, "O total vai até R$ 100.000.");
        if (diasTotal > 180) throw erro(HttpStatus.BAD_REQUEST, "A obra leva no máximo 180 dias de trabalho.");
        int min = FaixaObra.minimoEtapas(total), max = FaixaObra.maximoEtapas(total);
        if (lista.size() < min) throw erro(HttpStatus.BAD_REQUEST, "Para " + reais(total) + ", divida em pelo menos " + min + " etapas.");
        if (lista.size() > max) throw erro(HttpStatus.BAD_REQUEST, "Para " + reais(total) + ", use no máximo " + max + " etapas.");
        for (Map<String, Object> m : lista)
            if (FaixaObra.etapaGrandeDemais((BigDecimal) m.get("valor"), total)) throw erro(HttpStatus.BAD_REQUEST, "A partir de R$ 10.000, nenhuma etapa passa de 30% do total (" + reais(total.multiply(FaixaObra.MATERIAL_MAX).setScale(2, RoundingMode.HALF_UP)) + ").");
        exigirReputacao(pro, total);
        String material = String.valueOf(b.material());
        if (!Set.of("incluso", "cliente").contains(material)) throw erro(HttpStatus.BAD_REQUEST, "Diga quem compra o material.");
        String listaMat = b.listaMaterial() == null ? "" : b.listaMaterial().trim();
        if ("cliente".equals(material) && listaMat.length() < 5) throw erro(HttpStatus.BAD_REQUEST, "Mande a lista de material que o cliente precisa comprar.");
        if (listaMat.length() > 3000) throw erro(HttpStatus.BAD_REQUEST, "A lista de material vai até 3.000 letras.");
        LocalDate inicio;
        try { inicio = LocalDate.parse(String.valueOf(b.inicio())); } catch (RuntimeException e) { throw erro(HttpStatus.BAD_REQUEST, "Escolha a data de início."); }
        if (inicio.isBefore(LocalDate.now(FUSO).plusDays(1))) throw erro(HttpStatus.BAD_REQUEST, "O início precisa ser a partir de amanhã.");
        if (inicio.isAfter(LocalDate.now(FUSO).plusDays(180))) throw erro(HttpStatus.BAD_REQUEST, "O início vai até 180 dias daqui.");
        TreeSet<Integer> dias = new TreeSet<>(b.diasTrabalho() == null ? List.of() : b.diasTrabalho());
        if (dias.isEmpty() || dias.first() < 1 || dias.last() > 7) throw erro(HttpStatus.BAD_REQUEST, "Escolha os dias da semana de trabalho.");
        int[] horario = horario(b.horario());
        String obs = b.observacao() == null ? "" : b.observacao().trim();
        if (obs.length() > 2000) throw erro(HttpStatus.BAD_REQUEST, "A observação vai até 2.000 letras.");
        boolean contrato = FaixaObra.contrato(total);
        if (contrato && !Boolean.TRUE.equals(b.contratoAceito())) throw erro(HttpStatus.BAD_REQUEST, "A partir de R$ 5.000, aceite o contrato para enviar o orçamento.");
        List<LocalDate> diasObra = diasDeTrabalho(inicio, dias, diasTotal);
        long fim = diasObra.get(diasObra.size() - 1).atTime(horario[2], horario[3]).atZone(FUSO).toInstant().toEpochMilli();
        long agora = System.currentTimeMillis();
        orcamentos.findByPedidoIdAndStatus(pedidoId, Orcamento.ENVIADO).stream().filter(o -> o.getProfissionalId().equals(pro.getId()))
                .forEach(o -> { o.responder(Orcamento.RETIRADO, agora); orcamentos.save(o); });
        Orcamento o = orcamentos.save(new Orcamento(UUID.randomUUID().toString(), pedidoId, pro, json(lista), total, material, listaMat.isEmpty() ? null : listaMat,
                inicio.atStartOfDay(FUSO).toInstant().toEpochMilli(), dias.stream().map(String::valueOf).reduce((a, c) -> a + "," + c).orElse(""),
                b.horario().trim(), fim, obs.isEmpty() ? null : obs, contrato ? agora : null, agora));
        Long cliente = clienteId(p);
        avisar(cliente, pedidoId, "Novo orçamento", pro.getNome() + " mandou um orçamento de " + reais(total) + " em " + lista.size() + (lista.size() == 1 ? " etapa." : " etapas."), "orcamento:" + o.getId());
        if (cliente != null) eventos.enviar(cliente, "pedido", pedidoId);
        auditar(pro.getId(), "Mandou orçamento de " + reais(total) + " para a obra", pro.getId(), pedidoId);
        return orcamentoDto(o);
    }

    private void exigirReputacao(Usuario pro, BigDecimal total) {
        if (total.compareTo(FaixaObra.TRES) < 0) return;
        List<Pedido> feitos = pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc("", pro.getEmail()).stream()
                .filter(x -> pro.getEmail().equalsIgnoreCase(String.valueOf(x.getProEmail())) && "concluido".equals(x.getStatus())).toList();
        double nota = feitos.stream().filter(x -> x.getRating() != null).mapToDouble(Pedido::getRating).average().orElse(0);
        boolean cnpj = pro.getExtra().get("cnpj") instanceof String c && ResgateService.cnpjValido(c);
        if (total.compareTo(FaixaObra.CINCO) >= 0) {
            if (!(cnpj || feitos.size() >= 10 && nota >= 4.5))
                throw erro(HttpStatus.FORBIDDEN, "Orçamentos a partir de R$ 5.000 pedem 10 serviços concluídos com nota 4,5 ou mais, ou CNPJ/MEI cadastrado em Configurações. Você tem " + feitos.size() + " serviços" + (feitos.isEmpty() ? "" : " e nota " + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", nota)) + ".");
        } else if (feitos.size() < 3 || nota < 4)
            throw erro(HttpStatus.FORBIDDEN, "Orçamentos a partir de R$ 3.000 pedem 3 serviços concluídos com nota 4 ou mais. Você tem " + feitos.size() + " serviços" + (feitos.isEmpty() ? "" : " e nota " + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", nota)) + ".");
    }

    @Transactional
    public Map<String, Object> retirarOrcamento(String id, TaskgoPrincipal quem) {
        Orcamento o = orcamentos.findById(id).filter(x -> x.getProfissionalId().equals(quem.id())).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado."));
        if (!Orcamento.ENVIADO.equals(o.getStatus())) throw erro(HttpStatus.CONFLICT, "Este orçamento já foi respondido.");
        o.responder(Orcamento.RETIRADO, System.currentTimeMillis());
        return orcamentoDto(orcamentos.save(o));
    }

    @Transactional
    public Map<String, Object> recusarOrcamento(String id, TaskgoPrincipal quem) {
        Orcamento o = orcamentos.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado."));
        Pedido p = obra(o.getPedidoId());
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado.");
        if (!Orcamento.ENVIADO.equals(o.getStatus())) throw erro(HttpStatus.CONFLICT, "Este orçamento já foi respondido.");
        o.responder(Orcamento.RECUSADO, System.currentTimeMillis());
        avisar(o.getProfissionalId(), p.getId(), "Orçamento recusado", "O cliente não escolheu o seu orçamento.", "orcamento-recusado:" + o.getId());
        return orcamentoDto(orcamentos.save(o));
    }

    @Transactional
    public Map<String, Object> aceitarOrcamento(String id, TaskgoPrincipal quem, boolean contratoAceito) {
        Orcamento o = orcamentos.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado."));
        Pedido p = obra(o.getPedidoId());
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado.");
        if (!Orcamento.ENVIADO.equals(o.getStatus())) throw erro(HttpStatus.CONFLICT, "Este orçamento não está mais valendo.");
        if (!aberta(p)) throw erro(HttpStatus.CONFLICT, "Esta obra já escolheu um profissional.");
        if (FaixaObra.contrato(o.getTotal()) && !contratoAceito) throw erro(HttpStatus.BAD_REQUEST, "Leia e aceite o contrato para contratar.");
        if (o.getInicio() < LocalDate.now(FUSO).atStartOfDay(FUSO).toInstant().toEpochMilli()) throw erro(HttpStatus.CONFLICT, "A data de início deste orçamento já passou. Peça ao profissional um orçamento novo.");
        Usuario pro = usuarios.findById(o.getProfissionalId()).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        if (exigencia != null && !exigencia.liberado(pro)) throw erro(HttpStatus.CONFLICT, "Este profissional ainda não pode receber pedidos.");
        long agora = System.currentTimeMillis();
        String proId = pro.getProId() != null && !pro.getProId().isBlank() ? pro.getProId() : "pro_" + pro.getId();
        if (pedidos.aceitarSeDisponivel(p.getId(), pro.getEmail().trim().toLowerCase(Locale.ROOT), proId, pro.getNome(), agora) == 0)
            throw erro(HttpStatus.CONFLICT, "Esta obra já escolheu um profissional.");
        Orcamento aceito = orcamentos.findById(id).orElseThrow();
        aceito.responder(Orcamento.ACEITO, agora);
        if (FaixaObra.contrato(aceito.getTotal())) aceito.contratoCliente(agora);
        orcamentos.save(aceito);
        orcamentos.findByPedidoIdAndStatus(p.getId(), Orcamento.ENVIADO).forEach(x -> {
            x.responder(Orcamento.RECUSADO, agora); orcamentos.save(x);
            avisar(x.getProfissionalId(), p.getId(), "Orçamento não escolhido", "O cliente escolheu outro orçamento para a obra.", "orcamento-recusado:" + x.getId());
        });
        /* Etapas, com o abatimento da visita técnica deste profissional na primeira. */
        List<Map<String, Object>> lista = lerEtapas(aceito);
        BigDecimal visita = visitas.findByPedidoIdOrderByCriadaEmAsc(p.getId()).stream()
                .filter(v -> v.getProfissionalId().equals(pro.getId()) && Set.of(VisitaTecnica.PAGA, VisitaTecnica.REALIZADA, VisitaTecnica.LIBERADA).contains(v.getStatus()))
                .map(VisitaTecnica::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int i = 0; i < lista.size(); i++) {
            Map<String, Object> m = lista.get(i);
            EtapaObra e = new EtapaObra(UUID.randomUUID().toString(), p.getId(), aceito.getId(), i + 1, String.valueOf(m.get("nome")), (String) m.get("entrega"),
                    new BigDecimal(String.valueOf(m.get("valor"))), ((Number) m.get("dias")).intValue(), false);
            if (i == 0 && visita.signum() > 0) e.abater(visita.min(e.getValor()));
            etapas.save(e);
        }
        Pedido fechado = pedidos.findById(p.getId()).orElseThrow();
        fechado.setPrice(aceito.getTotal().doubleValue());
        fechado.setStatus("agendado");
        fechado.setSched(aceito.getInicio());
        Map<String, Object> e = new LinkedHashMap<>(fechado.getExtra());
        e.put("obraOrcamento", aceito.getId()); e.put("confirmacaoCliente", "confirmada");
        if (FaixaObra.revisaoEquipe(aceito.getTotal())) e.put("obraRevisao", "pendente");
        e.put("obraBloqueios", bloquearAgenda(pro.getId(), aceito, lista.stream().mapToInt(m -> ((Number) m.get("dias")).intValue()).sum(), PedidoContratadoController.nomeServico(fechado.getService())));
        fechado.setExtra(e);
        pedidos.save(fechado);
        avisar(pro.getId(), p.getId(), "Seu orçamento foi aceito", "Obra de " + reais(aceito.getTotal()) + " começando em " + DATA.format(Instant.ofEpochMilli(aceito.getInicio())) + ". A agenda já está bloqueada nos dias de trabalho.", "orcamento-aceito:" + aceito.getId());
        eventos.enviar(pro.getId(), "pedido", p.getId());
        if (FaixaObra.revisaoEquipe(aceito.getTotal())) avisarEquipe(p.getId(), "Obra para conferir", "Obra de " + reais(aceito.getTotal()) + " aceita. Confira antes de o cliente pagar a 1ª etapa.", "obra-revisao:" + p.getId());
        auditar(quem.id(), "Aceitou orçamento de " + reais(aceito.getTotal()) + " de " + pro.getNome(), quem.id(), p.getId());
        return ver(p.getId(), quem);
    }

    /* Bloqueia na agenda cada dia de trabalho, no horário combinado. */
    private List<String> bloquearAgenda(Long proId, Orcamento o, int diasTotal, String servico) {
        int[] h = horario(o.getHorario());
        TreeSet<Integer> dias = diasSemana(o.getDiasTrabalho());
        LocalDate inicio = Instant.ofEpochMilli(o.getInicio()).atZone(FUSO).toLocalDate();
        List<String> ids = new ArrayList<>();
        long agora = System.currentTimeMillis();
        for (LocalDate d : diasDeTrabalho(inicio, dias, diasTotal)) {
            long a = d.atTime(h[0], h[1]).atZone(FUSO).toInstant().toEpochMilli(), b = d.atTime(h[2], h[3]).atZone(FUSO).toInstant().toEpochMilli();
            ids.add(bloqueios.save(new BloqueioAgenda(UUID.randomUUID().toString(), proId, a, b, ("Obra: " + servico).substring(0, Math.min(80, ("Obra: " + servico).length())), agora)).getId());
        }
        return ids;
    }
    private void liberarAgenda(Pedido p) {
        if (!(p.getExtra().get("obraBloqueios") instanceof List<?> ids)) return;
        long agora = System.currentTimeMillis();
        for (Object id : ids) bloqueios.findById(String.valueOf(id)).filter(b -> b.getFim() > agora).ifPresent(bloqueios::delete);
    }

    static List<LocalDate> diasDeTrabalho(LocalDate inicio, Set<Integer> dias, int quantos) {
        List<LocalDate> r = new ArrayList<>();
        LocalDate d = inicio;
        for (int guarda = 0; r.size() < quantos && guarda < 2000; guarda++, d = d.plusDays(1))
            if (dias.contains(d.getDayOfWeek().getValue())) r.add(d);
        return r;
    }
    static TreeSet<Integer> diasSemana(String s) {
        TreeSet<Integer> r = new TreeSet<>();
        for (String x : String.valueOf(s).split(",")) try { r.add(Integer.parseInt(x.trim())); } catch (RuntimeException ignorar) { }
        if (r.isEmpty()) r.addAll(List.of(1, 2, 3, 4, 5));
        return r;
    }
    static int[] horario(String s) {
        if (s == null || !s.trim().matches("^([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d$")) throw erro(HttpStatus.BAD_REQUEST, "Informe o horário de trabalho, como 08:00-17:00.");
        LocalTime a = LocalTime.parse(s.trim().substring(0, 5)), b = LocalTime.parse(s.trim().substring(6));
        if (!b.isAfter(a.plusHours(1))) throw erro(HttpStatus.BAD_REQUEST, "O horário de trabalho precisa ter pelo menos 1 hora.");
        return new int[]{a.getHour(), a.getMinute(), b.getHour(), b.getMinute()};
    }

    /** Texto do contrato (a partir de R$ 5.000) que as duas partes aceitam. */
    @Transactional(readOnly = true)
    public String contrato(String orcamentoId, TaskgoPrincipal quem) {
        Orcamento o = orcamentos.findById(orcamentoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado."));
        Pedido p = obra(o.getPedidoId());
        if (!ehCliente(p, quem) && !o.getProfissionalId().equals(quem.id()) && !PapelEquipe.equipe(quem.tipo())) throw erro(HttpStatus.NOT_FOUND, "Orçamento não encontrado.");
        Usuario cli = usuarios.findById(clienteId(p) == null ? -1L : clienteId(p)).orElse(null);
        StringBuilder t = new StringBuilder();
        t.append("CONTRATO DE PRESTAÇÃO DE SERVIÇO PELA TASKGO\n\n");
        t.append("Contratante: ").append(cli == null ? "cliente" : cli.getNome()).append("\n");
        t.append("Profissional: ").append(o.getProfissionalNome()).append("\n");
        t.append("Serviço: ").append(PedidoContratadoController.nomeServico(p.getService())).append(" · pedido ").append(p.getId()).append("\n");
        t.append("Valor total: ").append(reais(o.getTotal())).append("\n");
        t.append("Início: ").append(DATA.format(Instant.ofEpochMilli(o.getInicio()))).append(" · previsão de término: ").append(DATA.format(Instant.ofEpochMilli(o.getPrevisaoFim()))).append("\n");
        t.append("Horário: ").append(o.getHorario()).append(" · material: ").append("incluso".equals(o.getMaterial()) ? "incluso no preço" : "comprado pelo cliente").append("\n\nEtapas:\n");
        int i = 1;
        for (Map<String, Object> m : lerEtapas(o)) t.append(i++).append(". ").append(m.get("nome")).append(" · ").append(reais(new BigDecimal(String.valueOf(m.get("valor"))))).append(" · ").append(m.get("dias")).append(" dia(s)").append(m.get("entrega") == null || String.valueOf(m.get("entrega")).isBlank() ? "" : " · entrega: " + m.get("entrega")).append("\n");
        t.append("\nRegras:\n");
        t.append("1. O contratante paga cada etapa antes de ela começar. A TaskGO guarda o valor e o libera ao profissional quando o contratante aprova a etapa, ou 24 horas depois da entrega sem resposta.\n");
        t.append("2. Um problema relatado numa etapa segura só essa etapa até a equipe da TaskGO decidir.\n");
        if (FaixaObra.garantia(o.getTotal())) t.append("3. Garantia: 8% do total fica retido por 7 dias depois da última etapa, para cobrir defeitos que apareçam logo após o fim.\n");
        t.append("4. Cancelamento: etapa aprovada fica com o profissional; etapa paga e não começada volta ao contratante; etapa em andamento é decidida pela equipe da TaskGO, com base no diário e nas fotos.\n");
        t.append("5. Mudanças de prazo ou de escopo só valem como aditivo aceito pelas duas partes na TaskGO.\n");
        t.append("6. Taxa da TaskGO sobre o valor: ").append(FaixaObra.taxa(o.getTotal()).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()).append("%, descontada do que o profissional recebe.\n");
        if (o.getContratoProEm() != null) t.append("\nAceito pelo profissional em ").append(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO).format(Instant.ofEpochMilli(o.getContratoProEm()))).append(".");
        if (o.getContratoClienteEm() != null) t.append("\nAceito pelo contratante em ").append(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO).format(Instant.ofEpochMilli(o.getContratoClienteEm()))).append(".");
        return t.toString();
    }

    /* ======================= visita técnica ======================= */

    @Transactional
    public Map<String, Object> proporVisita(String pedidoId, TaskgoPrincipal quem, BigDecimal valor, Long data) {
        Usuario pro = profissional(quem);
        Pedido p = obra(pedidoId);
        if (!aberta(p)) throw erro(HttpStatus.CONFLICT, "Esta obra já escolheu um profissional.");
        if (!(servicosPro != null ? servicosPro.faz(pro, p.getService()) : p.getService().equalsIgnoreCase(String.valueOf(pro.getService())))) throw erro(HttpStatus.FORBIDDEN, "Esta obra é de outra categoria.");
        if (valor == null || valor.compareTo(VISITA_MIN) < 0 || valor.compareTo(VISITA_MAX) > 0) throw erro(HttpStatus.BAD_REQUEST, "A visita técnica custa de R$ 50 a R$ 100.");
        long agora = System.currentTimeMillis();
        if (data == null || data < agora + 60 * 60 * 1000L || data > agora + 60 * DIA) throw erro(HttpStatus.BAD_REQUEST, "Marque a visita a partir de 1 hora daqui e em até 60 dias.");
        if (visitas.findByPedidoIdOrderByCriadaEmAsc(pedidoId).stream().anyMatch(v -> v.getProfissionalId().equals(pro.getId()) && Set.of(VisitaTecnica.PROPOSTA, VisitaTecnica.PAGA).contains(v.getStatus())))
            throw erro(HttpStatus.CONFLICT, "Você já tem uma visita proposta nesta obra.");
        VisitaTecnica v = visitas.save(new VisitaTecnica(UUID.randomUUID().toString(), pedidoId, pro, valor.setScale(2, RoundingMode.HALF_UP), data, agora));
        Long cliente = clienteId(p);
        avisar(cliente, pedidoId, "Visita técnica proposta", pro.getNome() + " quer ir medir antes de orçar: " + reais(v.getValor()) + ", abatidos se você contratar.", "visita:" + v.getId());
        if (cliente != null) eventos.enviar(cliente, "pedido", pedidoId);
        return visitaDto(v);
    }

    @Transactional
    public Object pagarVisita(String visitaId, TaskgoPrincipal quem, String metodo, String cartaoId) {
        VisitaTecnica v = visitas.findById(visitaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Visita não encontrada."));
        Pedido p = obra(v.getPedidoId());
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Visita não encontrada.");
        if (!VisitaTecnica.PROPOSTA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta visita não está esperando pagamento.");
        if (pagamentos.direto()) throw erro(HttpStatus.CONFLICT, "Aceite a visita e pague ao profissional depois que ele vier.");
        if ("dinheiro".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Obras não aceitam pagamento em dinheiro.");
        Cobranca c;
        alvo.set("visita:" + v.getId());
        try { c = pagamentos.cobrar(p, quem.id(), metodo, cartaoId, 1, v.getValor(), "visita"); } finally { alvo.remove(); }
        VisitaTecnica atual = visitas.findById(visitaId).orElseThrow();
        if (atual.getCobrancaId() == null || !atual.getCobrancaId().equals(c.getId())) { atual.cobranca(c.getId()); visitas.save(atual); }
        if (Cobranca.RECUSADA.equals(c.getStatus())) throw erro(HttpStatus.PAYMENT_REQUIRED, c.getMotivo() == null ? "O cartão não foi aprovado." : c.getMotivo());
        return PagamentoService.CobrancaDto.de(c);
    }

    @Transactional
    public Map<String, Object> responderVisita(String visitaId, TaskgoPrincipal quem, String acao) {
        VisitaTecnica v = visitas.findById(visitaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Visita não encontrada."));
        Pedido p = obra(v.getPedidoId());
        boolean cliente = ehCliente(p, quem), pro = v.getProfissionalId().equals(quem.id());
        if (!cliente && !pro) throw erro(HttpStatus.NOT_FOUND, "Visita não encontrada.");
        long agora = System.currentTimeMillis();
        switch (String.valueOf(acao)) {
            /* Pagamento direto: o cliente aceita a visita (sem pagar) e paga ao profissional depois que ele vier. */
            case "aceitar" -> {
                if (!pagamentos.direto() || !cliente || !VisitaTecnica.PROPOSTA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Não dá para aceitar esta visita.");
                v.situacao(VisitaTecnica.PAGA);
                avisar(v.getProfissionalId(), p.getId(), "Visita técnica aceita", "O cliente aceitou a visita de " + DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm").withZone(FUSO).format(Instant.ofEpochMilli(v.getData())) + ". O endereço já aparece na obra. Ele paga " + reais(v.getValor()) + " a você depois da visita.", "visita-aceita:" + v.getId());
                eventos.enviar(v.getProfissionalId(), "pedido", p.getId());
            }
            case "recebida" -> {
                if (!pagamentos.direto() || !pro || !VisitaTecnica.REALIZADA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Marque a visita como feita antes.");
                v.liberar(agora);
                if (dinheiro != null) dinheiro.registrarTaxa(v.getProfissionalId(), p.getId(), "visita:" + v.getId(), v.getValor().multiply(TAXA_VISITA));
                avisar(clienteId(p), p.getId(), "Visita técnica paga", v.getProfissionalNome() + " confirmou que recebeu " + reais(v.getValor()) + ".", "visita-recebida:" + v.getId());
            }
            case "recusar" -> {
                if (!cliente || !VisitaTecnica.PROPOSTA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Não dá para recusar esta visita.");
                v.situacao(VisitaTecnica.RECUSADA);
                avisar(v.getProfissionalId(), p.getId(), "Visita recusada", "O cliente não quis a visita técnica.", "visita-recusada:" + v.getId());
            }
            case "cancelar" -> {
                if (!Set.of(VisitaTecnica.PROPOSTA, VisitaTecnica.PAGA).contains(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta visita não pode mais ser cancelada.");
                if (VisitaTecnica.PAGA.equals(v.getStatus())) devolverVisita(v);
                v.situacao(VisitaTecnica.CANCELADA);
                avisar(cliente ? v.getProfissionalId() : clienteId(p), p.getId(), "Visita cancelada", (cliente ? "O cliente" : v.getProfissionalNome()) + " cancelou a visita técnica." + (cliente ? "" : " O valor voltou para você."), "visita-cancelada:" + v.getId());
            }
            case "realizada" -> {
                if (!pro || !VisitaTecnica.PAGA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Só a visita paga pode ser marcada como feita.");
                if (agora < v.getData() - 60 * 60 * 1000L) throw erro(HttpStatus.CONFLICT, "A visita está marcada para mais tarde.");
                v.realizar(agora);
                avisar(clienteId(p), p.getId(), pagamentos.direto() ? "Pague a visita técnica" : "Confirme a visita técnica", pagamentos.direto() ? v.getProfissionalNome() + " marcou a visita como feita. Pague " + reais(v.getValor()) + " por Pix: a chave está na obra." : v.getProfissionalNome() + " marcou a visita como feita. Confirme, ou o valor vai para ele em 24 horas.", "visita-feita:" + v.getId());
            }
            case "confirmar" -> {
                if (!cliente || !VisitaTecnica.REALIZADA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "A visita ainda não foi marcada como feita.");
                if (pagamentos.direto()) throw erro(HttpStatus.CONFLICT, "Pague a visita ao profissional; ele confirma quando receber.");
                liberarVisita(v);
            }
            default -> throw erro(HttpStatus.BAD_REQUEST, "Ação inválida.");
        }
        return visitaDto(visitas.save(v));
    }

    private void liberarVisita(VisitaTecnica v) {
        BigDecimal taxa = v.getValor().multiply(TAXA_VISITA).setScale(2, RoundingMode.HALF_UP);
        creditar(v.getProfissionalId(), v.getValor().subtract(taxa), v.getPedidoId(), "Visita técnica paga", "repasse-visita:" + v.getId());
        v.liberar(System.currentTimeMillis());
    }
    private void devolverVisita(VisitaTecnica v) {
        pagamentos.cobranca(v.getCobrancaId()).filter(c -> Cobranca.PAGA.equals(c.getStatus())).ifPresent(c -> devolver(c, c.liquido()));
    }
    /* Devolve pelo provedor; se ele ainda não aceitar (pagamento recente, bloqueio cautelar), a devolução entra na fila e é repetida a cada minuto. */
    private void devolver(Cobranca c, BigDecimal valor) {
        if (valor.signum() <= 0) return;
        try { pagamentos.devolverCobranca(c, valor); }
        catch (PagamentoService.EstornoAdiado adiado) {
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "DEVOLUCAO_PENDENTE", valor, "PENDENTE", adiado.referencia(c), System.currentTimeMillis()));
            avisar(c.getClienteId(), c.getPedidoId(), "Devolução em andamento", reais(valor) + " voltam para você assim que o banco liberar.", "devolucao-pendente:" + c.getId() + ":" + valor);
        }
    }

    /* ======================= pagamento das etapas ======================= */

    @Transactional
    public Object pagarEtapa(String etapaId, TaskgoPrincipal quem, String metodo, String cartaoId, Integer parcelas) {
        EtapaObra e = etapas.findById(etapaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Etapa não encontrada."));
        Pedido p = obra(e.getPedidoId());
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Etapa não encontrada.");
        if (Set.of("cancelado", "concluido").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta obra está encerrada.");
        if (!EtapaObra.PENDENTE.equals(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta etapa já foi paga.");
        if (pagamentos.direto()) throw erro(HttpStatus.CONFLICT, "Você paga cada etapa ao profissional depois de aprová-la.");
        EtapaObra proxima = etapas.findByPedidoIdOrderByOrdemAsc(p.getId()).stream().filter(x -> EtapaObra.PENDENTE.equals(x.getStatus())).findFirst().orElse(null);
        if (proxima == null || !proxima.getId().equals(e.getId())) throw erro(HttpStatus.CONFLICT, "Pague as etapas na ordem: primeiro a etapa " + (proxima == null ? "" : proxima.getOrdem()) + ".");
        if ("pendente".equals(p.getExtra().get("obraRevisao"))) throw erro(HttpStatus.CONFLICT, "A equipe da TaskGO está conferindo a obra. O pagamento abre assim que ela aprovar.");
        if ("reprovada".equals(p.getExtra().get("obraRevisao"))) throw erro(HttpStatus.CONFLICT, "A equipe da TaskGO não aprovou esta obra.");
        if ("dinheiro".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "Obras não aceitam pagamento em dinheiro.");
        BigDecimal valor = e.aPagar();
        long agora = System.currentTimeMillis();
        if (valor.signum() <= 0) { e.pagar(BigDecimal.ZERO, agora); etapas.save(e); aposPagar(p, e); return Map.of("status", "paga"); }
        int n = parcelas == null ? 1 : parcelas;
        if (n < 1 || n > 12) throw erro(HttpStatus.BAD_REQUEST, "Escolha de 1 a 12 parcelas.");
        BigDecimal cobrado = "cartao".equals(metodo) ? pagamentos.comJuros(valor, n) : valor;
        Cobranca c;
        alvo.set("etapa:" + e.getId());
        try { c = pagamentos.cobrar(p, quem.id(), metodo, cartaoId, "cartao".equals(metodo) ? n : 1, cobrado, "etapa"); } finally { alvo.remove(); }
        EtapaObra atual = etapas.findById(etapaId).orElseThrow();
        if (atual.getCobrancaId() == null || !atual.getCobrancaId().equals(c.getId())) { atual.cobranca(c.getId()); etapas.save(atual); }
        if (Cobranca.RECUSADA.equals(c.getStatus())) throw erro(HttpStatus.PAYMENT_REQUIRED, c.getMotivo() == null ? "O cartão não foi aprovado." : c.getMotivo());
        return PagamentoService.CobrancaDto.de(c);
    }

    /** Chamado pelo PagamentoService quando uma cobrança de etapa ou de visita é confirmada. */
    @Transactional
    public void cobrancaPaga(Cobranca c) {
        long agora = System.currentTimeMillis();
        String a = alvo.get();
        if ("visita".equals(c.getTipo())) {
            VisitaTecnica v = visitas.findByCobrancaId(c.getId()).orElseGet(() -> a != null && a.startsWith("visita:") ? visitas.findById(a.substring(7)).orElse(null) : null);
            if (v == null || !VisitaTecnica.PROPOSTA.equals(v.getStatus())) { devolverSobra(c); return; }
            v.cobranca(c.getId()); v.situacao(VisitaTecnica.PAGA); visitas.save(v);
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "PAGAMENTO", c.getValor(), "RETIDO", c.getPedidoId(), agora));
            avisar(v.getProfissionalId(), v.getPedidoId(), "Visita técnica paga", "O cliente pagou a visita de " + DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm").withZone(FUSO).format(Instant.ofEpochMilli(v.getData())) + ". O endereço já aparece na obra.", "visita-paga:" + v.getId());
            eventos.enviar(v.getProfissionalId(), "pedido", v.getPedidoId());
            return;
        }
        EtapaObra e = etapas.findByCobrancaId(c.getId()).orElseGet(() -> a != null && a.startsWith("etapa:") ? etapas.findById(a.substring(6)).orElse(null) : null);
        if (e == null || !EtapaObra.PENDENTE.equals(e.getStatus())) { devolverSobra(c); return; }
        Pedido p = pedidos.findById(e.getPedidoId()).orElse(null);
        if (p == null || "cancelado".equals(p.getStatus())) { devolverSobra(c); return; }
        BigDecimal valor = e.aPagar();
        e.cobranca(c.getId()); e.pagar(valor, agora); etapas.save(e);
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "PAGAMENTO", c.getValor(), "RETIDO", e.getPedidoId(), agora));
        if (c.getValor().compareTo(valor) > 0)
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "JUROS", c.getValor().subtract(valor), "CONCLUIDA", e.getPedidoId(), agora));
        auditar(c.getClienteId(), "Pagou a etapa " + e.getOrdem() + " (" + e.getNome() + "): " + reais(c.getValor()), c.getClienteId(), e.getPedidoId());
        aposPagar(p, e);
    }
    private void aposPagar(Pedido p, EtapaObra e) {
        idDoEmail(p.getProEmail()).ifPresent(pro -> {
            avisar(pro, p.getId(), "Etapa paga: pode começar", "O cliente pagou a etapa " + e.getOrdem() + " (" + e.getNome() + "). O valor está garantido com a TaskGO.", "etapa-paga:" + e.getId());
            eventos.enviar(pro, "pedido", p.getId());
        });
    }
    /* Pagamento que chegou para algo que não espera mais (etapa já paga, obra cancelada): volta para quem pagou. */
    private void devolverSobra(Cobranca c) { devolver(c, c.liquido()); }

    /* ======================= execução ======================= */

    @Transactional
    public Map<String, Object> iniciarEtapa(String etapaId, TaskgoPrincipal quem) {
        EtapaObra e = etapaDoAtendente(etapaId, quem);
        boolean direto = pagamentos.direto();
        if (direto) {
            /* Pagamento direto: começa sem pagar; a etapa anterior precisa estar aprovada e paga. */
            if (!EtapaObra.PENDENTE.equals(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta etapa já começou.");
            Pedido ob = pedidos.findById(e.getPedidoId()).orElseThrow();
            if ("pendente".equals(ob.getExtra().get("obraRevisao"))) throw erro(HttpStatus.CONFLICT, "A equipe da TaskGO está conferindo a obra. A 1ª etapa começa assim que ela aprovar.");
            if ("reprovada".equals(ob.getExtra().get("obraRevisao"))) throw erro(HttpStatus.CONFLICT, "A equipe da TaskGO não aprovou esta obra.");
            boolean pagas = etapas.findByPedidoIdOrderByOrdemAsc(e.getPedidoId()).stream().filter(x -> x.getOrdem() < e.getOrdem())
                    .allMatch(x -> EtapaObra.APROVADA.equals(x.getStatus()) && x.getPagaEm() != null || Set.of(EtapaObra.CANCELADA, EtapaObra.DEVOLVIDA).contains(x.getStatus()));
            if (!pagas) throw erro(HttpStatus.CONFLICT, "A etapa anterior precisa estar aprovada e paga antes de começar esta.");
        } else {
            if (!EtapaObra.PAGA.equals(e.getStatus())) throw erro(HttpStatus.CONFLICT, EtapaObra.PENDENTE.equals(e.getStatus()) ? "Espere o cliente pagar esta etapa." : "Esta etapa já começou.");
            boolean anterioresFeitas = etapas.findByPedidoIdOrderByOrdemAsc(e.getPedidoId()).stream().filter(x -> x.getOrdem() < e.getOrdem())
                    .allMatch(x -> Set.of(EtapaObra.ENTREGUE, EtapaObra.APROVADA).contains(x.getStatus()));
            if (!anterioresFeitas) throw erro(HttpStatus.CONFLICT, "Entregue a etapa anterior antes de começar esta.");
        }
        e.iniciar(System.currentTimeMillis()); etapas.save(e);
        Pedido p = pedidos.findById(e.getPedidoId()).orElseThrow();
        if (!"em_andamento".equals(p.getStatus())) { p.setStatus("em_andamento"); pedidos.save(p); }
        avisar(clienteId(p), p.getId(), "Etapa começou", "A etapa " + e.getOrdem() + " (" + e.getNome() + ") começou. Acompanhe pelo diário da obra.", "etapa-inicio:" + e.getId());
        return etapaDto(e);
    }

    /** Material incluso no preço: até 30% da etapa paga sai na hora, com a foto da nota. */
    @Transactional
    public Map<String, Object> adiantarMaterial(String etapaId, TaskgoPrincipal quem, BigDecimal valor, String fotoNota) {
        EtapaObra e = etapaDoAtendente(etapaId, quem);
        Pedido p = pedidos.findById(e.getPedidoId()).orElseThrow();
        Orcamento o = orcamentos.findById(e.getOrcamentoId()).orElseThrow();
        if (!"incluso".equals(o.getMaterial())) throw erro(HttpStatus.CONFLICT, "Nesta obra o material é comprado pelo cliente.");
        if (pagamentos.direto()) throw erro(HttpStatus.CONFLICT, "No pagamento direto, combine com o cliente a compra do material.");
        if (!Set.of(EtapaObra.PAGA, EtapaObra.ANDAMENTO).contains(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Só em etapa paga e ainda não entregue.");
        if (e.getMaterialLiberado().signum() > 0) throw erro(HttpStatus.CONFLICT, "O adiantamento de material desta etapa já foi feito.");
        BigDecimal max = e.getPago().multiply(FaixaObra.MATERIAL_MAX).setScale(2, RoundingMode.DOWN);
        if (valor == null || valor.signum() <= 0 || valor.compareTo(max) > 0) throw erro(HttpStatus.BAD_REQUEST, "O adiantamento vai até " + reais(max) + " (30% da etapa).");
        if (fotoNota == null || fotoNota.isBlank()) throw erro(HttpStatus.BAD_REQUEST, "Mande a foto da nota do material.");
        FotoPedido f = fotos.salvar(p.getId(), quem.id(), "obra", fotoNota);
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        e.liberarMaterial(v); etapas.save(e);
        BigDecimal liquido = v.subtract(v.multiply(FaixaObra.taxa(total(p))).setScale(2, RoundingMode.HALF_UP));
        creditar(quem.id(), liquido, p.getId(), "Adiantamento de material", "material:" + e.getId());
        avisar(clienteId(p), p.getId(), "Adiantamento de material", "Liberamos " + reais(v) + " da etapa " + e.getOrdem() + " para a compra de material. A nota está nas fotos da obra.", "material-cliente:" + e.getId() + ":" + f.getId());
        auditar(quem.id(), "Recebeu adiantamento de material de " + reais(v), quem.id(), p.getId());
        return etapaDto(e);
    }

    @Transactional
    public Map<String, Object> entregarEtapa(String etapaId, TaskgoPrincipal quem, List<String> fotosEtapa) {
        EtapaObra e = etapaDoAtendente(etapaId, quem);
        if (!Set.of(EtapaObra.ANDAMENTO, EtapaObra.PAGA).contains(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta etapa não está em andamento.");
        if (fotosEtapa == null || fotosEtapa.isEmpty() || fotosEtapa.size() > 6) throw erro(HttpStatus.BAD_REQUEST, "Mande de 1 a 6 fotos da etapa pronta.");
        List<String> ids = new ArrayList<>();
        for (String f : fotosEtapa) ids.add(fotos.salvar(e.getPedidoId(), quem.id(), "obra", f).getId());
        long agora = System.currentTimeMillis();
        e.entregar(String.join(",", ids), agora, agora + PRAZO_APROVAR); etapas.save(e);
        Pedido p = pedidos.findById(e.getPedidoId()).orElseThrow();
        if (!"em_andamento".equals(p.getStatus())) { p.setStatus("em_andamento"); pedidos.save(p); }
        Long cli = clienteId(p);
        avisar(cli, p.getId(), "Aprove a etapa " + e.getOrdem(), "A etapa " + e.getNome() + " foi entregue, com fotos. " + (pagamentos.direto() ? "Aprove ou relate um problema em até 24 horas; depois de aprovar, pague " + reais(e.aPagar()) + " ao profissional." : "Aprove ou relate um problema em até 24 horas; sem resposta, o valor vai para o profissional."), "etapa-entregue:" + e.getId());
        if (cli != null) eventos.enviar(cli, "pedido", p.getId());
        return etapaDto(e);
    }

    @Transactional
    public Map<String, Object> aprovarEtapa(String etapaId, TaskgoPrincipal quem) {
        EtapaObra e = etapas.findById(etapaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Etapa não encontrada."));
        Pedido p = obra(e.getPedidoId());
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Etapa não encontrada.");
        if (!EtapaObra.ENTREGUE.equals(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta etapa não está esperando aprovação.");
        aprovar(e, p);
        return etapaDto(e);
    }

    @Transactional
    public Map<String, Object> problemaEtapa(String etapaId, TaskgoPrincipal quem, String texto) {
        EtapaObra e = etapas.findById(etapaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Etapa não encontrada."));
        Pedido p = obra(e.getPedidoId());
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Etapa não encontrada.");
        if (!Set.of(EtapaObra.ENTREGUE, EtapaObra.ANDAMENTO).contains(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Só dá para relatar problema em etapa em andamento ou entregue.");
        String t = texto == null ? "" : texto.trim();
        if (t.length() < 10 || t.length() > 1000) throw erro(HttpStatus.BAD_REQUEST, "Conte o problema em 10 a 1000 letras.");
        e.problema(t); etapas.save(e);
        idDoEmail(p.getProEmail()).ifPresent(pro -> avisar(pro, p.getId(), "Problema na etapa " + e.getOrdem(), "O cliente relatou um problema. O valor desta etapa fica retido até a equipe da TaskGO decidir; as etapas aprovadas continuam com você.", "etapa-problema:" + e.getId()));
        avisarEquipe(p.getId(), "Problema numa etapa de obra", "Etapa " + e.getOrdem() + " (" + reais(e.retido()) + " retidos). Veja o diário e as fotos em Filas.", "obra-problema:" + e.getId());
        auditar(quem.id(), "Relatou problema na etapa " + e.getOrdem() + " da obra", quem.id(), p.getId());
        return etapaDto(e);
    }

    /* Libera o que está retido na etapa (menos a garantia, na última etapa). */
    private void aprovar(EtapaObra e, Pedido p) {
        if (etapas.reservarAprovacao(e.getId(), EtapaObra.ENTREGUE, EtapaObra.APROVADA) == 0) return;
        long agora = System.currentTimeMillis();
        if (pagamentos.direto()) {
            /* Pagamento direto: aprovada, o cliente paga a etapa ao profissional, que confirma o recebimento. */
            e.aprovar(agora); etapas.save(e);
            avisar(clienteId(p), p.getId(), "Pague a etapa " + e.getOrdem(), "Etapa aprovada. Pague " + reais(e.aPagar()) + " ao profissional por Pix: a chave está na obra.", "etapa-pagar:" + e.getId());
            idDoEmail(p.getProEmail()).ifPresent(pro -> { avisar(pro, p.getId(), "Etapa " + e.getOrdem() + " aprovada", "O cliente vai pagar " + reais(e.aPagar()) + " direto a você. Confirme na obra quando receber.", "etapa-aprovada-pro:" + e.getId()); eventos.enviar(pro, "pedido", p.getId()); });
            return;
        }
        BigDecimal total = total(p), retido = e.retido();
        List<EtapaObra> todas = etapas.findByPedidoIdOrderByOrdemAsc(p.getId());
        boolean ultima = todas.stream().filter(x -> !x.getId().equals(e.getId())).allMatch(x -> Set.of(EtapaObra.APROVADA, EtapaObra.DEVOLVIDA, EtapaObra.CANCELADA).contains(x.getStatus()));
        BigDecimal garantia = BigDecimal.ZERO;
        if (ultima && FaixaObra.garantia(total) && !(p.getExtra().get("obraGarantia") instanceof Map<?, ?>))
            garantia = total.multiply(FaixaObra.GARANTIA).setScale(2, RoundingMode.HALF_UP).min(retido);
        BigDecimal libera = retido.subtract(garantia);
        e.liberar(libera); e.aprovar(agora); etapas.save(e);
        if (libera.signum() > 0) creditar(proId(p), libera.subtract(libera.multiply(FaixaObra.taxa(total)).setScale(2, RoundingMode.HALF_UP)), p.getId(), "Etapa " + e.getOrdem() + " aprovada", "etapa-repasse:" + e.getId());
        if (garantia.signum() > 0) {
            Map<String, Object> g = new LinkedHashMap<>(); g.put("valor", garantia); g.put("ate", agora + PRAZO_GARANTIA); g.put("status", "retida"); g.put("etapaId", e.getId());
            Map<String, Object> ex = new LinkedHashMap<>(p.getExtra()); ex.put("obraGarantia", g); p.setExtra(ex); pedidos.save(p);
        }
        verificarConclusao(p);
    }

    /** Pagamento direto: o profissional confirma que recebeu a etapa aprovada; a taxa da faixa vira taxa a pagar. */
    @Transactional
    public Map<String, Object> etapaRecebida(String etapaId, TaskgoPrincipal quem) {
        EtapaObra e = etapaDoAtendente(etapaId, quem);
        if (!pagamentos.direto()) throw erro(HttpStatus.CONFLICT, "Esta obra é paga pela TaskGO.");
        if (!EtapaObra.APROVADA.equals(e.getStatus()) || e.getPagaEm() != null) throw erro(HttpStatus.CONFLICT, "Só a etapa aprovada e ainda não paga.");
        Pedido p = pedidos.findById(e.getPedidoId()).orElseThrow();
        long agora = System.currentTimeMillis();
        BigDecimal valor = e.aPagar();
        e.recebidoDireto(valor, agora); etapas.save(e);
        if (dinheiro != null) dinheiro.registrarTaxa(quem.id(), p.getId(), "etapa:" + e.getId(), valor.multiply(FaixaObra.taxa(total(p))));
        avisar(clienteId(p), p.getId(), "Etapa " + e.getOrdem() + " paga", "O profissional confirmou que recebeu " + reais(valor) + ".", "etapa-recebida:" + e.getId());
        auditar(quem.id(), "Recebeu direto a etapa " + e.getOrdem() + ": " + reais(valor), quem.id(), p.getId());
        verificarConclusao(p);
        return etapaDto(e);
    }
    /** Quanto o cliente tem a pagar direto agora (etapas aprovadas e visitas feitas, ainda não pagas). */
    @Transactional(readOnly = true)
    public BigDecimal temAPagar(String pedidoId) {
        BigDecimal r = etapas.findByPedidoIdOrderByOrdemAsc(pedidoId).stream().filter(x -> EtapaObra.APROVADA.equals(x.getStatus()) && x.getPagaEm() == null)
                .map(EtapaObra::aPagar).reduce(BigDecimal.ZERO, BigDecimal::add);
        return r.add(visitas.findByPedidoIdOrderByCriadaEmAsc(pedidoId).stream().filter(v -> VisitaTecnica.REALIZADA.equals(v.getStatus()))
                .map(VisitaTecnica::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    /** Antes de contratar: quem fez a visita técnica ainda não paga. */
    @Transactional(readOnly = true)
    public Long proDaVisitaAPagar(String pedidoId) {
        return visitas.findByPedidoIdOrderByCriadaEmAsc(pedidoId).stream().filter(v -> VisitaTecnica.REALIZADA.equals(v.getStatus())).map(VisitaTecnica::getProfissionalId).findFirst().orElse(null);
    }

    private void verificarConclusao(Pedido p) {
        List<EtapaObra> todas = etapas.findByPedidoIdOrderByOrdemAsc(p.getId());
        if (todas.isEmpty() || "cancelado".equals(p.getStatus())) return;
        if (!todas.stream().allMatch(x -> Set.of(EtapaObra.APROVADA, EtapaObra.DEVOLVIDA, EtapaObra.CANCELADA).contains(x.getStatus()))) return;
        if (pagamentos.direto() && todas.stream().anyMatch(x -> EtapaObra.APROVADA.equals(x.getStatus()) && x.getPagaEm() == null)) return;
        Pedido atual = pedidos.findById(p.getId()).orElseThrow();
        if ("concluido".equals(atual.getStatus())) return;
        atual.setStatus("concluido"); atual.setCompletedAt(System.currentTimeMillis()); atual.setProgress(1.0);
        pedidos.save(atual);
        liberarAgenda(atual);
        Long cli = clienteId(atual);
        Object g = atual.getExtra().get("obraGarantia");
        avisar(cli, atual.getId(), "Obra concluída", g instanceof Map<?, ?> m ? "Todas as etapas foram aprovadas. " + reais(new BigDecimal(String.valueOf(m.get("valor")))) + " ficam de garantia por 7 dias: se aparecer um defeito, avise na obra." : "Todas as etapas foram aprovadas. Avalie o profissional.", "obra-concluida:" + atual.getId());
        idDoEmail(atual.getProEmail()).ifPresent(pro -> avisar(pro, atual.getId(), "Obra concluída", "Todas as etapas foram aprovadas" + (g instanceof Map<?, ?> ? "; a garantia é liberada em 7 dias se nenhum defeito for relatado." : "."), "obra-concluida-pro:" + atual.getId()));
    }

    /* ======================= vários dias: diário e aditivos ======================= */

    @Transactional
    public Map<String, Object> chegada(String pedidoId, TaskgoPrincipal quem, Double lat, Double lng) {
        Pedido p = obra(pedidoId);
        if (!ehAtendente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        if (!Set.of("agendado", "em_andamento").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta obra não está em andamento.");
        if (diario.findByPedidoIdOrderByChegadaEmDesc(pedidoId).stream().anyMatch(d -> d.getSaidaEm() == null)) throw erro(HttpStatus.CONFLICT, "Marque a saída do dia anterior antes.");
        Integer dist = null;
        Double plat = ChamarAgoraService.numero(p.getExtra().get("lat")), plng = ChamarAgoraService.numero(p.getExtra().get("lng"));
        if (lat != null && lng != null && plat != null && plng != null) dist = (int) Math.round(ChamarAgoraService.distanciaKm(lat, lng, plat, plng) * 1000);
        long agora = System.currentTimeMillis();
        DiarioObra d = diario.save(new DiarioObra(UUID.randomUUID().toString(), pedidoId, quem.id(), LocalDate.now(FUSO).toString(), agora, dist));
        Long cli = clienteId(p);
        avisar(cli, pedidoId, "Chegou na obra", p.getProfessionalName() + " marcou a chegada" + (dist == null ? "." : dist <= 300 ? " no local." : ", a " + (dist >= 1000 ? String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", dist / 1000.0) : dist + " m") + " do endereço."), "diario-chegada:" + d.getId());
        if (cli != null) eventos.enviar(cli, "pedido", pedidoId);
        return diarioDto(d);
    }

    @Transactional
    public Map<String, Object> saida(String pedidoId, TaskgoPrincipal quem, String texto, String foto) {
        Pedido p = obra(pedidoId);
        if (!ehAtendente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        DiarioObra d = diario.findByPedidoIdOrderByChegadaEmDesc(pedidoId).stream().filter(x -> x.getSaidaEm() == null).findFirst()
                .orElseThrow(() -> erro(HttpStatus.CONFLICT, "Marque a chegada antes."));
        String t = texto == null ? "" : texto.trim();
        if (t.length() < 5 || t.length() > 1000) throw erro(HttpStatus.BAD_REQUEST, "Conte em uma linha o que foi feito hoje (5 a 1000 letras).");
        String fotoId = foto == null || foto.isBlank() ? null : fotos.salvar(pedidoId, quem.id(), "obra", foto).getId();
        d.sair(System.currentTimeMillis(), t, fotoId);
        Long cli = clienteId(p);
        if (cli != null) eventos.enviar(cli, "pedido", pedidoId);
        return diarioDto(diario.save(d));
    }

    @Transactional
    public Map<String, Object> proporAditivo(String pedidoId, TaskgoPrincipal quem, String tipo, Integer dias, BigDecimal valor, String nome, String motivo) {
        Pedido p = obra(pedidoId);
        if (!ehAtendente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        if (!Set.of("agendado", "em_andamento").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta obra não está em andamento.");
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < 10 || m.length() > 1000) throw erro(HttpStatus.BAD_REQUEST, "Explique o motivo em 10 a 1000 letras.");
        if (dias == null || dias < 0 || dias > 60) throw erro(HttpStatus.BAD_REQUEST, "De 0 a 60 dias a mais.");
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", UUID.randomUUID().toString()); a.put("tipo", tipo); a.put("dias", dias); a.put("motivo", m); a.put("status", "proposto"); a.put("em", System.currentTimeMillis());
        if ("etapa".equals(tipo)) {
            String n = nome == null ? "" : nome.trim();
            if (n.length() < 2 || n.length() > 120) throw erro(HttpStatus.BAD_REQUEST, "Dê um nome à etapa nova.");
            if (valor == null || valor.signum() <= 0) throw erro(HttpStatus.BAD_REQUEST, "Informe o valor da etapa nova.");
            if (dias < 1) throw erro(HttpStatus.BAD_REQUEST, "A etapa nova leva pelo menos 1 dia.");
            BigDecimal novo = total(p).add(valor);
            if (novo.compareTo(BigDecimal.valueOf(100_000)) > 0) throw erro(HttpStatus.BAD_REQUEST, "O total da obra vai até R$ 100.000.");
            if (FaixaObra.etapaGrandeDemais(valor, novo)) throw erro(HttpStatus.BAD_REQUEST, "A partir de R$ 10.000, nenhuma etapa passa de 30% do total.");
            a.put("nome", n); a.put("valor", valor.setScale(2, RoundingMode.HALF_UP));
        } else if ("dias".equals(tipo)) {
            if (dias < 1) throw erro(HttpStatus.BAD_REQUEST, "Peça pelo menos 1 dia a mais.");
        } else throw erro(HttpStatus.BAD_REQUEST, "Escolha mais dias ou uma etapa nova.");
        List<Map<String, Object>> lista = new ArrayList<>(aditivos(p));
        if (lista.stream().anyMatch(x -> "proposto".equals(x.get("status")))) throw erro(HttpStatus.CONFLICT, "Já há um aditivo esperando o cliente.");
        lista.add(a);
        Map<String, Object> ex = new LinkedHashMap<>(p.getExtra()); ex.put("obraAditivos", lista); p.setExtra(ex); pedidos.save(p);
        Long cli = clienteId(p);
        avisar(cli, pedidoId, "Pedido de aditivo", "etapa".equals(tipo) ? "O profissional propõe uma etapa nova: " + a.get("nome") + " por " + reais((BigDecimal) a.get("valor")) + "." : "O profissional pede " + dias + (dias == 1 ? " dia" : " dias") + " a mais.", "aditivo:" + a.get("id"));
        if (cli != null) eventos.enviar(cli, "pedido", pedidoId);
        return a;
    }

    @Transactional
    public Map<String, Object> responderAditivo(String pedidoId, String aditivoId, TaskgoPrincipal quem, boolean aceitar) {
        Pedido p = obra(pedidoId);
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        List<Map<String, Object>> lista = new ArrayList<>(aditivos(p));
        Map<String, Object> a = lista.stream().filter(x -> aditivoId.equals(x.get("id"))).findFirst().orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aditivo não encontrado."));
        if (!"proposto".equals(a.get("status"))) throw erro(HttpStatus.CONFLICT, "Este aditivo já foi respondido.");
        Map<String, Object> novo = new LinkedHashMap<>(a); novo.put("status", aceitar ? "aceito" : "recusado"); novo.put("respondidoEm", System.currentTimeMillis());
        lista.set(lista.indexOf(a), novo);
        Map<String, Object> ex = new LinkedHashMap<>(p.getExtra()); ex.put("obraAditivos", lista);
        if (aceitar) {
            Orcamento o = orcamentos.findById(String.valueOf(p.getExtra().get("obraOrcamento"))).orElseThrow();
            int dias = ((Number) a.get("dias")).intValue();
            if ("etapa".equals(a.get("tipo"))) {
                BigDecimal v = new BigDecimal(String.valueOf(a.get("valor")));
                int ordem = etapas.findByPedidoIdOrderByOrdemAsc(p.getId()).stream().mapToInt(EtapaObra::getOrdem).max().orElse(0) + 1;
                etapas.save(new EtapaObra(UUID.randomUUID().toString(), p.getId(), o.getId(), ordem, String.valueOf(a.get("nome")), String.valueOf(a.get("motivo")), v, dias, true));
                o.novoTotal(o.getTotal().add(v)); p.setPrice(o.getTotal().doubleValue());
            }
            if (dias > 0) {
                TreeSet<Integer> ds = diasSemana(o.getDiasTrabalho());
                LocalDate depois = Instant.ofEpochMilli(o.getPrevisaoFim()).atZone(FUSO).toLocalDate().plusDays(1);
                List<LocalDate> extras = diasDeTrabalho(depois, ds, dias);
                int[] h = horario(o.getHorario());
                o.novaPrevisao(extras.get(extras.size() - 1).atTime(h[2], h[3]).atZone(FUSO).toInstant().toEpochMilli());
                List<Object> ids = new ArrayList<>(p.getExtra().get("obraBloqueios") instanceof List<?> l ? l : List.of());
                long agora = System.currentTimeMillis();
                Long proId = proId(p);
                for (LocalDate d : extras)
                    ids.add(bloqueios.save(new BloqueioAgenda(UUID.randomUUID().toString(), proId, d.atTime(h[0], h[1]).atZone(FUSO).toInstant().toEpochMilli(), d.atTime(h[2], h[3]).atZone(FUSO).toInstant().toEpochMilli(), "Obra (aditivo)", agora)).getId());
                ex.put("obraBloqueios", ids);
            }
            orcamentos.save(o);
        }
        p.setExtra(ex); pedidos.save(p);
        idDoEmail(p.getProEmail()).ifPresent(pro -> { avisar(pro, pedidoId, aceitar ? "Aditivo aceito" : "Aditivo recusado", aceitar ? "O cliente aceitou o aditivo." : "O cliente recusou o aditivo.", "aditivo-resposta:" + aditivoId); eventos.enviar(pro, "pedido", pedidoId); });
        return novo;
    }

    /* ======================= garantia ======================= */

    @Transactional
    public Map<String, Object> defeito(String pedidoId, TaskgoPrincipal quem, String texto) {
        Pedido p = obra(pedidoId);
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        if (!(p.getExtra().get("obraGarantia") instanceof Map<?, ?> g) || !"retida".equals(g.get("status"))) throw erro(HttpStatus.CONFLICT, "Esta obra não tem garantia retida.");
        if (((Number) g.get("ate")).longValue() < System.currentTimeMillis()) throw erro(HttpStatus.CONFLICT, "O prazo da garantia acabou.");
        String t = texto == null ? "" : texto.trim();
        if (t.length() < 10 || t.length() > 1000) throw erro(HttpStatus.BAD_REQUEST, "Conte o defeito em 10 a 1000 letras.");
        Map<String, Object> ng = new LinkedHashMap<>(); g.forEach((k, v) -> ng.put(String.valueOf(k), v));
        ng.put("status", "defeito"); ng.put("defeito", t); ng.put("defeitoEm", System.currentTimeMillis());
        Map<String, Object> ex = new LinkedHashMap<>(p.getExtra()); ex.put("obraGarantia", ng); p.setExtra(ex); pedidos.save(p);
        idDoEmail(p.getProEmail()).ifPresent(pro -> avisar(pro, pedidoId, "Defeito relatado na obra", "O cliente relatou um defeito. Combine pela conversa o conserto; a garantia fica retida até a equipe decidir.", "obra-defeito:" + pedidoId));
        avisarEquipe(pedidoId, "Defeito na garantia de uma obra", "Garantia de " + reais(new BigDecimal(String.valueOf(ng.get("valor")))) + " retida. Decida em Filas.", "obra-defeito-equipe:" + pedidoId);
        return ng;
    }

    /* ======================= cancelamento ======================= */

    /** Cancelar no meio: aprovada fica com o profissional; paga e não começada volta; em andamento vai para a equipe. */
    @Transactional
    public Pedido cancelar(String pedidoId, TaskgoPrincipal quem, String motivo) {
        Pedido p = obra(pedidoId);
        boolean cliente = ehCliente(p, quem), atende = ehAtendente(p, quem);
        if (!cliente && !atende) throw erro(HttpStatus.FORBIDDEN, "Esta obra não pertence a esta conta.");
        if (Set.of("cancelado", "concluido").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta obra já está encerrada.");
        long agora = System.currentTimeMillis();
        boolean mediacao = false;
        for (EtapaObra e : etapas.findByPedidoIdOrderByOrdemAsc(pedidoId)) {
            if (pagamentos.direto()) { if (!EtapaObra.APROVADA.equals(e.getStatus())) { e.situacao(EtapaObra.CANCELADA); etapas.save(e); } continue; }
            switch (e.getStatus()) {
                case EtapaObra.PENDENTE -> { cancelarCobrancaAberta(e); e.situacao(EtapaObra.CANCELADA); }
                case EtapaObra.PAGA -> { devolverEtapa(e, e.retido()); e.situacao(EtapaObra.DEVOLVIDA); }
                case EtapaObra.ANDAMENTO, EtapaObra.ENTREGUE, EtapaObra.PROBLEMA -> { e.situacao(EtapaObra.MEDIACAO); mediacao = true; }
                default -> { }
            }
            etapas.save(e);
        }
        for (VisitaTecnica v : visitas.findByPedidoIdOrderByCriadaEmAsc(pedidoId)) {
            if (VisitaTecnica.PAGA.equals(v.getStatus())) { devolverVisita(v); v.situacao(VisitaTecnica.CANCELADA); visitas.save(v); }
            else if (VisitaTecnica.PROPOSTA.equals(v.getStatus())) { v.situacao(VisitaTecnica.CANCELADA); visitas.save(v); }
        }
        orcamentos.findByPedidoIdAndStatus(pedidoId, Orcamento.ENVIADO).forEach(o -> { o.responder(Orcamento.RETIRADO, agora); orcamentos.save(o); });
        Pedido atual = pedidos.findById(pedidoId).orElseThrow();
        atual.setStatus("cancelado");
        atual.setCancelReason(motivo == null || motivo.isBlank() ? (cliente ? "Cliente cancelou a obra" : "Profissional cancelou a obra") : motivo.trim().substring(0, Math.min(200, motivo.trim().length())));
        if (mediacao) { Map<String, Object> ex = new LinkedHashMap<>(atual.getExtra()); ex.put("obraMediacao", true); atual.setExtra(ex); }
        pedidos.save(atual);
        liberarAgenda(atual);
        Long outro = cliente ? proId(atual) : clienteId(atual);
        avisar(outro, pedidoId, "Obra cancelada", (cliente ? "O cliente" : "O profissional") + " cancelou a obra." + (mediacao ? " A etapa em andamento vai para a equipe da TaskGO decidir, com base no diário e nas fotos." : ""), "obra-cancelada:" + pedidoId + ":" + outro);
        if (mediacao) avisarEquipe(pedidoId, "Obra cancelada no meio", "Há etapa em andamento para decidir. Veja em Filas.", "obra-mediacao:" + pedidoId);
        auditar(quem.id(), "Cancelou a obra", quem.id(), pedidoId);
        return atual;
    }
    private void cancelarCobrancaAberta(EtapaObra e) {
        pagamentos.cobranca(e.getCobrancaId()).filter(c -> Cobranca.PENDENTE.equals(c.getStatus())).ifPresent(c -> pagamentos.cancelarAberta(c));
    }
    private void devolverEtapa(EtapaObra e, BigDecimal valor) {
        if (valor.signum() <= 0) return;
        Cobranca c = pagamentos.cobranca(e.getCobrancaId()).orElse(null);
        if (c != null) devolver(c, valor);
        e.devolver(valor);
    }

    /* ======================= equipe ======================= */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> fila() {
        List<Map<String, Object>> r = new ArrayList<>();
        Set<String> vistos = new java.util.HashSet<>();
        List<EtapaObra> abertas = new ArrayList<>(etapas.findByStatus(EtapaObra.PROBLEMA));
        abertas.addAll(etapas.findByStatus(EtapaObra.MEDIACAO));
        for (EtapaObra e : abertas) if (vistos.add(e.getPedidoId())) pedidos.findById(e.getPedidoId()).ifPresent(p -> r.add(linhaFila(p, "etapa")));
        for (Pedido p : pedidos.findByStatusIgnoreCase("agendado")) if ("pendente".equals(p.getExtra().get("obraRevisao")) && vistos.add(p.getId())) r.add(linhaFila(p, "revisao"));
        for (Pedido p : pedidos.concluidosComGarantia())
            if (p.getExtra().get("obraGarantia") instanceof Map<?, ?> g && "defeito".equals(g.get("status")) && vistos.add(p.getId())) r.add(linhaFila(p, "garantia"));
        r.sort(Comparator.comparingLong(m -> ((Number) m.getOrDefault("criado", 0L)).longValue()));
        return r;
    }
    private Map<String, Object> linhaFila(Pedido p, String motivo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("motivo", motivo); m.put("servico", PedidoContratadoController.nomeServico(p.getService())); m.put("total", p.getPrice());
        m.put("cliente", usuarios.findById(clienteId(p) == null ? -1L : clienteId(p)).map(Usuario::getNome).orElse("Cliente")); m.put("profissional", p.getProfessionalName());
        m.put("criado", p.getCreated()); m.put("status", p.getStatus());
        m.put("etapas", etapas.findByPedidoIdOrderByOrdemAsc(p.getId()).stream().map(this::etapaDto).toList());
        m.put("diario", diario.findByPedidoIdOrderByChegadaEmDesc(p.getId()).stream().limit(30).map(this::diarioDto).toList());
        m.put("garantia", p.getExtra().get("obraGarantia"));
        orcamentos.findById(String.valueOf(p.getExtra().get("obraOrcamento"))).ifPresent(o -> m.put("orcamento", orcamentoDto(o)));
        return m;
    }

    @Transactional
    public void revisar(String pedidoId, boolean aprovar, String motivo, Long equipe) {
        Pedido p = obra(pedidoId);
        if (!"pendente".equals(p.getExtra().get("obraRevisao"))) throw erro(HttpStatus.CONFLICT, "Esta obra não está esperando revisão.");
        if (!aprovar && (motivo == null || motivo.trim().length() < 5)) throw erro(HttpStatus.BAD_REQUEST, "Diga o motivo (o cliente e o profissional vão ver).");
        Map<String, Object> ex = new LinkedHashMap<>(p.getExtra()); ex.put("obraRevisao", aprovar ? "aprovada" : "reprovada"); if (!aprovar) ex.put("obraRevisaoMotivo", motivo.trim());
        p.setExtra(ex); pedidos.save(p);
        if (aprovar) {
            avisar(clienteId(p), pedidoId, "Obra conferida", "A equipe conferiu a obra. Você já pode pagar a 1ª etapa.", "obra-revisada:" + pedidoId);
            idDoEmail(p.getProEmail()).ifPresent(pro -> avisar(pro, pedidoId, "Obra conferida", "A equipe conferiu a obra. Assim que o cliente pagar a 1ª etapa, você pode começar.", "obra-revisada-pro:" + pedidoId));
        } else {
            Pedido atual = pedidos.findById(pedidoId).orElseThrow();
            atual.setStatus("cancelado"); atual.setCancelReason("Reprovada pela equipe: " + motivo.trim());
            pedidos.save(atual); liberarAgenda(atual);
            etapas.findByPedidoIdOrderByOrdemAsc(pedidoId).forEach(e -> { e.situacao(EtapaObra.CANCELADA); etapas.save(e); });
            avisar(clienteId(p), pedidoId, "Obra não aprovada", "A equipe da TaskGO não aprovou esta obra: " + motivo.trim(), "obra-reprovada:" + pedidoId);
            idDoEmail(p.getProEmail()).ifPresent(pro -> avisar(pro, pedidoId, "Obra não aprovada", "A equipe da TaskGO não aprovou esta obra: " + motivo.trim(), "obra-reprovada-pro:" + pedidoId));
        }
        auditar(clienteId(p), (aprovar ? "Equipe aprovou a obra" : "Equipe reprovou a obra: " + motivo.trim()), equipe, pedidoId);
    }

    /** Equipe decide uma etapa com problema ou em mediação: quanto vai ao profissional; o resto volta ao cliente. */
    @Transactional
    public Map<String, Object> decidirEtapa(String etapaId, BigDecimal aoProfissional, Long equipe) {
        EtapaObra e = etapas.findById(etapaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Etapa não encontrada."));
        if (!Set.of(EtapaObra.PROBLEMA, EtapaObra.MEDIACAO).contains(e.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta etapa não está esperando decisão.");
        Pedido p = obra(e.getPedidoId());
        BigDecimal retido = e.retido(), pro = aoProfissional == null ? BigDecimal.ZERO : aoProfissional.setScale(2, RoundingMode.HALF_UP);
        if (pro.signum() < 0 || pro.compareTo(retido) > 0) throw erro(HttpStatus.BAD_REQUEST, "Escolha de R$ 0 a " + reais(retido) + " para o profissional.");
        boolean mediacao = EtapaObra.MEDIACAO.equals(e.getStatus());
        if (pro.signum() > 0) { e.liberar(pro); creditar(proId(p), pro.subtract(pro.multiply(FaixaObra.taxa(total(p))).setScale(2, RoundingMode.HALF_UP)), p.getId(), "Decisão da equipe na etapa " + e.getOrdem(), "etapa-decisao:" + e.getId()); }
        devolverEtapa(e, retido.subtract(pro));
        e.situacao(pro.signum() > 0 ? EtapaObra.APROVADA : EtapaObra.DEVOLVIDA);
        etapas.save(e);
        avisar(clienteId(p), p.getId(), "Decisão da equipe", "Etapa " + e.getOrdem() + ": " + reais(retido.subtract(pro)) + " voltaram para você" + (pro.signum() > 0 ? " e " + reais(pro) + " foram para o profissional." : "."), "etapa-decidida-cli:" + e.getId());
        idDoEmail(p.getProEmail()).ifPresent(x -> avisar(x, p.getId(), "Decisão da equipe", "Etapa " + e.getOrdem() + ": " + (pro.signum() > 0 ? reais(pro) + " liberados para você (menos a taxa)." : "o valor voltou para o cliente."), "etapa-decidida-pro:" + e.getId()));
        auditar(clienteId(p), "Equipe decidiu a etapa " + e.getOrdem() + ": " + reais(pro) + " ao profissional", equipe, p.getId());
        if (!mediacao) verificarConclusao(p);
        else if (etapas.findByPedidoIdOrderByOrdemAsc(p.getId()).stream().noneMatch(x -> EtapaObra.MEDIACAO.equals(x.getStatus()))) {
            Pedido atual = pedidos.findById(p.getId()).orElseThrow(); Map<String, Object> ex = new LinkedHashMap<>(atual.getExtra()); ex.remove("obraMediacao"); atual.setExtra(ex); pedidos.save(atual);
        }
        return etapaDto(e);
    }

    @Transactional
    public Map<String, Object> decidirGarantia(String pedidoId, BigDecimal aoProfissional, Long equipe) {
        Pedido p = obra(pedidoId);
        if (!(p.getExtra().get("obraGarantia") instanceof Map<?, ?> g) || !"defeito".equals(g.get("status"))) throw erro(HttpStatus.CONFLICT, "Esta garantia não está esperando decisão.");
        BigDecimal valor = new BigDecimal(String.valueOf(g.get("valor"))), pro = aoProfissional == null ? BigDecimal.ZERO : aoProfissional.setScale(2, RoundingMode.HALF_UP);
        if (pro.signum() < 0 || pro.compareTo(valor) > 0) throw erro(HttpStatus.BAD_REQUEST, "Escolha de R$ 0 a " + reais(valor) + " para o profissional.");
        fecharGarantia(p, pro, valor.subtract(pro), "decidida");
        auditar(clienteId(p), "Equipe decidiu a garantia: " + reais(pro) + " ao profissional", equipe, pedidoId);
        return Map.of("aoProfissional", pro, "aoCliente", valor.subtract(pro));
    }

    private void fecharGarantia(Pedido p, BigDecimal aoPro, BigDecimal aoCliente, String status) {
        Map<?, ?> g = (Map<?, ?>) p.getExtra().get("obraGarantia");
        EtapaObra e = etapas.findById(String.valueOf(g.get("etapaId"))).orElse(null);
        if (aoPro.signum() > 0) {
            if (e != null) { e.liberar(aoPro); etapas.save(e); }
            creditar(proId(p), aoPro.subtract(aoPro.multiply(FaixaObra.taxa(total(p))).setScale(2, RoundingMode.HALF_UP)), p.getId(), "Garantia liberada", "garantia:" + p.getId());
        }
        if (aoCliente.signum() > 0 && e != null) { devolverEtapa(e, aoCliente); etapas.save(e); avisar(clienteId(p), p.getId(), "Garantia devolvida", reais(aoCliente) + " da garantia voltaram para você.", "garantia-cliente:" + p.getId()); }
        Map<String, Object> ng = new LinkedHashMap<>(); g.forEach((k, v) -> ng.put(String.valueOf(k), v)); ng.put("status", status); ng.put("fechadaEm", System.currentTimeMillis());
        Map<String, Object> ex = new LinkedHashMap<>(p.getExtra()); ex.put("obraGarantia", ng); p.setExtra(ex); pedidos.save(p);
    }

    /* ======================= prazos (a cada minuto) ======================= */

    /* Uma transação por item: um item com dado ruim não desfaz nem trava os outros. */
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTx(org.springframework.transaction.PlatformTransactionManager m) { this.tx = new org.springframework.transaction.support.TransactionTemplate(m); }
    private void porItem(String id, Runnable r) {
        try { if (tx == null) r.run(); else tx.executeWithoutResult(s -> r.run()); }
        catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(ObraService.class).warn("Prazo de obra {} falhou: {}", id, e.getMessage()); }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void prazos() {
        long agora = System.currentTimeMillis();
        for (String id : transacoes.findByTipoAndStatus("DEVOLUCAO_PENDENTE", "PENDENTE").stream().map(TransacaoFinanceira::getId).toList())
            porItem(id, () -> transacoes.findById(id).filter(t -> "PENDENTE".equals(t.getStatus())).ifPresent(t -> {
                /* Resolvida (feita, ou a reserva foi liberada e não há o que devolver) ou ainda em andamento com a referência nova. */
                String nova = pagamentos.retomarDevolucao(t.getReferencia(), t.getValor());
                if (nova == null) t.situacao("CONCLUIDA"); else t.referencia(nova);
                transacoes.save(t);
            }));
        for (String id : etapas.findByStatus(EtapaObra.ENTREGUE).stream().map(EtapaObra::getId).toList())
            porItem(id, () -> etapas.findById(id).filter(e -> EtapaObra.ENTREGUE.equals(e.getStatus()) && e.getLiberarApos() != null && e.getLiberarApos() <= agora)
                .ifPresent(e -> pedidos.findById(e.getPedidoId()).ifPresent(p -> { if (!"cancelado".equals(p.getStatus())) aprovar(e, p); })));
        if (!pagamentos.direto()) for (String id : visitas.findByStatus(VisitaTecnica.REALIZADA).stream().map(VisitaTecnica::getId).toList())
            porItem(id, () -> visitas.findById(id).filter(v -> VisitaTecnica.REALIZADA.equals(v.getStatus()) && v.getRealizadaEm() != null && v.getRealizadaEm() + PRAZO_APROVAR <= agora)
                .ifPresent(v -> { liberarVisita(v); visitas.save(v); }));
        for (String id : pedidos.concluidosComGarantia().stream().map(Pedido::getId).toList())
            porItem(id, () -> pedidos.findById(id).ifPresent(p -> garantiaVencida(p, agora)));
    }

    private void garantiaVencida(Pedido p, long agora) {
        {
            if (p.getExtra().get("obraGarantia") instanceof Map<?, ?> g && "retida".equals(g.get("status")) && ((Number) g.get("ate")).longValue() <= agora) {
                BigDecimal valor = new BigDecimal(String.valueOf(g.get("valor")));
                fecharGarantia(p, valor, BigDecimal.ZERO, "liberada");
                idDoEmail(p.getProEmail()).ifPresent(pro -> avisar(pro, p.getId(), "Garantia liberada", "Os 7 dias passaram sem defeito: a garantia caiu na sua carteira.", "garantia-liberada:" + p.getId()));
            }
        }
    }

    /* ======================= apoio ======================= */

    private void creditar(Long usuarioId, BigDecimal valor, String pedidoId, String titulo, String chave) {
        if (usuarioId == null || valor.signum() <= 0) return;
        Carteira c = carteiras.findByUsuarioIdForUpdate(usuarioId).orElseGet(() -> carteiras.save(new Carteira(usuarioId)));
        c.creditar(valor); carteiras.save(c);
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), usuarioId, "REPASSE", valor, "CONCLUIDA", pedidoId, System.currentTimeMillis()));
        avisar(usuarioId, pedidoId, titulo, reais(valor) + " caíram na sua carteira.", chave);
        eventos.enviar(usuarioId, "pagamento", pedidoId);
        if (dinheiro != null) dinheiro.descontarTaxas(usuarioId);
    }
    private Pedido obra(String id) {
        Pedido p = pedidos.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Obra não encontrada."));
        if (!eh(p)) throw erro(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        return p;
    }
    private static boolean aberta(Pedido p) { return "aguardando".equals(p.getStatus()) && p.getProEmail() == null; }
    private static boolean ehCliente(Pedido p, TaskgoPrincipal q) { return String.valueOf(q.id()).equals(p.getClientId()) || q.email().equalsIgnoreCase(String.valueOf(p.getClientEmail())); }
    private static boolean ehAtendente(Pedido p, TaskgoPrincipal q) { return p.getProEmail() != null && q.email().equalsIgnoreCase(p.getProEmail()); }
    private Usuario profissional(TaskgoPrincipal q) {
        if (!"profissional".equalsIgnoreCase(q.tipo())) throw erro(HttpStatus.FORBIDDEN, "Só profissionais mandam orçamento.");
        if (moderacao != null) moderacao.exigirLiberado(q.id());
        if (exigencia != null) exigencia.exigirParaAtender(q.id());
        return usuarios.findById(q.id()).orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Conta não encontrada."));
    }
    private EtapaObra etapaDoAtendente(String etapaId, TaskgoPrincipal q) {
        EtapaObra e = etapas.findById(etapaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Etapa não encontrada."));
        Pedido p = obra(e.getPedidoId());
        if (!ehAtendente(p, q)) throw erro(HttpStatus.NOT_FOUND, "Etapa não encontrada.");
        if ("cancelado".equals(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta obra foi cancelada.");
        return e;
    }
    private static BigDecimal total(Pedido p) { return BigDecimal.valueOf(p.getPrice() == null ? 0 : p.getPrice()).setScale(2, RoundingMode.HALF_UP); }
    private static Long clienteId(Pedido p) { try { return Long.valueOf(p.getClientId()); } catch (RuntimeException e) { return null; } }
    private Long proId(Pedido p) { return idDoEmail(p.getProEmail()).orElse(null); }
    private java.util.Optional<Long> idDoEmail(String email) { return email == null ? java.util.Optional.empty() : usuarios.findByEmailIgnoreCase(email).map(Usuario::getId); }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> aditivos(Pedido p) {
        return p.getExtra().get("obraAditivos") instanceof List<?> l ? l.stream().filter(x -> x instanceof Map).map(x -> (Map<String, Object>) x).toList() : List.of();
    }
    private static List<Map<String, Object>> lerEtapas(Orcamento o) {
        try { return JSON.readValue(o.getEtapas(), new TypeReference<List<Map<String, Object>>>() {}); } catch (Exception e) { return List.of(); }
    }
    private static String json(Object o) { try { return JSON.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }

    Map<String, Object> orcamentoDto(Orcamento o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("profissionalId", o.getProfissionalId()); m.put("profissionalNome", o.getProfissionalNome());
        m.put("etapas", lerEtapas(o)); m.put("total", o.getTotal()); m.put("material", o.getMaterial()); m.put("listaMaterial", o.getListaMaterial());
        m.put("inicio", o.getInicio()); m.put("diasTrabalho", diasSemana(o.getDiasTrabalho())); m.put("horario", o.getHorario()); m.put("previsaoFim", o.getPrevisaoFim());
        m.put("observacao", o.getObservacao()); m.put("status", o.getStatus()); m.put("criadoEm", o.getCriadoEm());
        m.put("contrato", FaixaObra.contrato(o.getTotal())); m.put("contratoProEm", o.getContratoProEm()); m.put("contratoClienteEm", o.getContratoClienteEm());
        m.put("taxa", FaixaObra.taxa(o.getTotal()));
        usuarios.findById(o.getProfissionalId()).ifPresent(u -> {
            m.put("profissionalTemFoto", FotoController.foto(u) != null); m.put("verificado", VerificacaoController.verificado(u));
            m.put("pro", planos != null && planos.ehPro(u.getId()));
            List<Pedido> feitos = pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc("", u.getEmail()).stream()
                    .filter(x -> u.getEmail().equalsIgnoreCase(String.valueOf(x.getProEmail())) && "concluido".equals(x.getStatus())).toList();
            m.put("servicosFeitos", feitos.size());
            feitos.stream().filter(x -> x.getRating() != null).mapToDouble(Pedido::getRating).average().ifPresent(n -> m.put("nota", Math.round(n * 10) / 10.0));
        });
        return m;
    }
    Map<String, Object> visitaDto(VisitaTecnica v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId()); m.put("profissionalId", v.getProfissionalId()); m.put("profissionalNome", v.getProfissionalNome()); m.put("valor", v.getValor());
        m.put("data", v.getData()); m.put("status", v.getStatus()); m.put("cobrancaId", v.getCobrancaId()); m.put("realizadaEm", v.getRealizadaEm());
        return m;
    }
    Map<String, Object> etapaDto(EtapaObra e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("ordem", e.getOrdem()); m.put("nome", e.getNome()); m.put("entrega", e.getEntrega()); m.put("valor", e.getValor());
        m.put("desconto", e.getDesconto()); m.put("aPagar", e.aPagar()); m.put("dias", e.getDias()); m.put("status", e.getStatus()); m.put("pago", e.getPago());
        m.put("liberado", e.getLiberado()); m.put("devolvido", e.getDevolvido()); m.put("materialLiberado", e.getMaterialLiberado()); m.put("aditivo", e.isAditivo());
        m.put("fotos", e.getFotos() == null || e.getFotos().isBlank() ? List.of() : List.of(e.getFotos().split(","))); m.put("problema", e.getProblema());
        m.put("cobrancaId", e.getCobrancaId()); m.put("pagaEm", e.getPagaEm()); m.put("iniciadaEm", e.getIniciadaEm()); m.put("entregueEm", e.getEntregueEm());
        m.put("liberarApos", e.getLiberarApos()); m.put("aprovadaEm", e.getAprovadaEm());
        return m;
    }
    Map<String, Object> diarioDto(DiarioObra d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId()); m.put("dia", d.getDia()); m.put("chegadaEm", d.getChegadaEm()); m.put("saidaEm", d.getSaidaEm());
        m.put("distanciaM", d.getDistanciaM()); m.put("texto", d.getTexto()); m.put("fotoId", d.getFotoId());
        return m;
    }
    private void avisar(Long usuarioId, String pedidoId, String titulo, String texto, String chave) {
        if (notificacoes == null || usuarioId == null) return;
        try { notificacoes.criar(usuarioId, pedidoId, "pedido", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
    private void avisarEquipe(String pedidoId, String titulo, String texto, String chave) {
        for (String tipo : List.of("suporte", "financeiro"))
            usuarios.findByTipoIgnoreCaseOrderByNomeAsc(tipo).forEach(a -> { avisar(a.getId(), pedidoId, titulo, texto, chave + ":" + a.getId()); eventos.enviar(a.getId(), "notificacao", pedidoId); });
    }
    private void auditar(Long conta, String texto, Long autor, String pedidoId) { if (moderacao != null && conta != null) moderacao.registrar(conta, "pagamento", texto, autor, pedidoId); }
    static String reais(BigDecimal v) { return PagamentoService.reais(v); }
    private static ResponseStatusException erro(HttpStatus s, String m) { return new ResponseStatusException(s, m); }
}
