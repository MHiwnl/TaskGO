package com.taskgo.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serviço recorrente: o mesmo serviço várias vezes (2 a 30), em dias da semana escolhidos, num horário fixo.
 * Só para serviços cobrados por dia ou visita. O preço do pedido é o valor de cada vez; o total é valor × vezes.
 * 1. O pedido sai sem cobrança; quem aceita tem a agenda bloqueada em todas as datas.
 * 2. O cliente paga por semana, antes de ela começar; o valor fica retido.
 * 3. O profissional marca cada vez como feita; o cliente confirma (ou em 24 h) e o valor da vez vai para a carteira, menos a taxa.
 *    Se ele não veio, a vez volta para o cliente. Feita contestada vai para o suporte.
 * 4. Cancelar devolve as vezes pagas e não feitas; as feitas são pagas ao profissional.
 */
@Service
public class RecorrenteService {
    static final ZoneId FUSO = ZoneId.of("America/Recife");
    static final int MIN_VEZES = 2, MAX_VEZES = 30, MAX_DIAS_PARA_COMECAR = 60;
    static final long HORA = 60L * 60 * 1000, DIA = 24 * HORA, PRAZO_CONFIRMAR = DIA, DURACAO = 2 * HORA;
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");

    private final VisitaRecorrenteRepository visitas;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final CarteiraRepository carteiras;
    private final TransacaoFinanceiraRepository transacoes;
    private final BloqueioAgendaRepository bloqueios;
    private final PagamentoService pagamentos;
    private final EventosService eventos;
    private NotificacaoService notificacoes;
    private ModeracaoService moderacao;
    private DinheiroService dinheiro;
    private AgendaService agenda;
    /* Semana sendo paga agora: liga a cobrança às vezes antes de o pagamento imediato (cartão, carteira) voltar. */
    private final ThreadLocal<String> alvo = new ThreadLocal<>();

    public RecorrenteService(VisitaRecorrenteRepository visitas, PedidoContratadoRepository pedidos, UsuarioRepository usuarios, CarteiraRepository carteiras,
                             TransacaoFinanceiraRepository transacoes, BloqueioAgendaRepository bloqueios, PagamentoService pagamentos, EventosService eventos) {
        this.visitas = visitas; this.pedidos = pedidos; this.usuarios = usuarios; this.carteiras = carteiras; this.transacoes = transacoes;
        this.bloqueios = bloqueios; this.pagamentos = pagamentos; this.eventos = eventos;
    }
    @Autowired(required = false) void setNotificacoes(NotificacaoService n) { this.notificacoes = n; }
    @Autowired(required = false) void setModeracao(ModeracaoService m) { this.moderacao = m; }
    @Autowired(required = false) void setDinheiro(DinheiroService d) { this.dinheiro = d; }
    @Autowired(required = false) void setAgenda(AgendaService a) { this.agenda = a; }
    /* Pagamento direto: cada vez é paga ao profissional depois de acontecer; pela TaskGO passa só a taxa. */
    private boolean direto() { return pagamentos.direto(); }
    /** Quanto o cliente tem a pagar direto agora (vezes confirmadas e ainda não pagas). */
    @Transactional(readOnly = true)
    public BigDecimal temAPagar(String pedidoId) { return soma(visitas.findByPedidoIdOrderByOrdemAsc(pedidoId), Set.of(VisitaRecorrente.A_PAGAR)); }

    public static boolean eh(Pedido p) { return p != null && p.getExtra().get("recorrente") instanceof Map; }

    /* ======================= criação ======================= */

    /** Plano pedido pelo cliente, já conferido: vezes, dias da semana (1 = segunda ... 7 = domingo), início e horário. */
    public record Plano(int vezes, TreeSet<Integer> dias, LocalDate inicio, LocalTime hora) {
        List<LocalDate> datas() { return ObraService.diasDeTrabalho(inicio, dias, vezes); }
        Map<String, Object> comoExtra(double valor) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vezes", vezes); m.put("dias", new ArrayList<>(dias)); m.put("inicio", inicio.toString()); m.put("hora", hora.toString());
            m.put("total", BigDecimal.valueOf(valor).multiply(BigDecimal.valueOf(vezes)).setScale(2, RoundingMode.HALF_UP));
            List<LocalDate> d = datas();
            m.put("fim", d.get(d.size() - 1).toString());
            m.put("semanas", semana(d.get(0), d.get(d.size() - 1)));
            return m;
        }
    }

    /** Confere o bloco "Serviço recorrente" do pedido. Lança 400 com a explicação quando algo não fecha. */
    static Plano plano(Object bruto, String servico, LocalDate hoje) {
        if (!(bruto instanceof Map<?, ?> m)) throw erro(HttpStatus.BAD_REQUEST, "Informe quantas vezes, os dias e o horário do serviço recorrente.");
        if (!CatalogoServicos.recorrente(servico)) throw erro(HttpStatus.BAD_REQUEST, "Este serviço não é cobrado por dia ou visita: ele não pode ser recorrente.");
        int vezes;
        try { vezes = ((Number) m.get("vezes")).intValue(); } catch (RuntimeException e) { throw erro(HttpStatus.BAD_REQUEST, "Informe quantas vezes, de 2 a 30."); }
        if (vezes < MIN_VEZES || vezes > MAX_VEZES) throw erro(HttpStatus.BAD_REQUEST, "Informe de 2 a 30 vezes.");
        TreeSet<Integer> dias = new TreeSet<>();
        if (m.get("dias") instanceof List<?> l) for (Object x : l) if (x instanceof Number n && n.intValue() >= 1 && n.intValue() <= 7) dias.add(n.intValue());
        if (dias.isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Escolha pelo menos um dia da semana.");
        LocalDate inicio;
        try { inicio = LocalDate.parse(String.valueOf(m.get("inicio"))); } catch (RuntimeException e) { throw erro(HttpStatus.BAD_REQUEST, "Informe a data de início."); }
        if (!inicio.isAfter(hoje)) throw erro(HttpStatus.BAD_REQUEST, "O serviço recorrente começa a partir de amanhã.");
        if (inicio.isAfter(hoje.plusDays(MAX_DIAS_PARA_COMECAR))) throw erro(HttpStatus.BAD_REQUEST, "Comece em até 60 dias.");
        LocalTime hora;
        try { hora = LocalTime.parse(String.valueOf(m.get("hora"))); } catch (RuntimeException e) { throw erro(HttpStatus.BAD_REQUEST, "Informe o horário, como 08:00."); }
        if (hora.isBefore(LocalTime.of(6, 0)) || hora.isAfter(LocalTime.of(21, 0))) throw erro(HttpStatus.BAD_REQUEST, "Escolha um horário entre 06:00 e 21:00.");
        return new Plano(vezes, dias, inicio, hora.withSecond(0).withNano(0));
    }
    static int semana(LocalDate primeira, LocalDate d) {
        LocalDate segunda = primeira.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) (ChronoUnit.DAYS.between(segunda, d) / 7) + 1;
    }
    static long quando(LocalDate d, String hora) { return d.atTime(LocalTime.parse(hora)).atZone(FUSO).toInstant().toEpochMilli(); }

    /** Cria as vezes do pedido recorrente recém-criado. */
    @Transactional
    public void gerar(Pedido p) {
        if (!eh(p)) return;
        Plano pl = plano(p.getExtra().get("recorrente"), p.getService(), LocalDate.now(FUSO).minusDays(1));
        List<LocalDate> datas = pl.datas();
        BigDecimal valor = BigDecimal.valueOf(p.getPrice()).setScale(2, RoundingMode.HALF_UP);
        int i = 1;
        for (LocalDate d : datas) visitas.save(new VisitaRecorrente(UUID.randomUUID().toString(), p.getId(), i++, d.toString(), semana(datas.get(0), d), valor));
    }

    /* ======================= aceite ======================= */

    /** Antes de aceitar: todas as datas precisam estar livres na agenda do profissional. */
    @Transactional(readOnly = true)
    public void exigirLivre(Usuario pro, Pedido p) {
        if (agenda == null) return;
        String hora = hora(p);
        for (VisitaRecorrente v : visitas.findByPedidoIdOrderByOrdemAsc(p.getId())) {
            String motivo = agenda.conflito(pro.getId(), pro.getEmail(), quando(LocalDate.parse(v.getData()), hora), p.getId());
            if (motivo != null) throw erro(HttpStatus.CONFLICT, "A vez de " + DM.format(LocalDate.parse(v.getData())) + " bate com a sua agenda. " + motivo);
        }
    }

    /** Depois do aceite: o pedido fica agendado e a agenda é bloqueada em todas as datas. */
    @Transactional
    public Pedido aoAceitar(Pedido p, Usuario pro) {
        String hora = hora(p);
        List<String> ids = new ArrayList<>();
        long agora = System.currentTimeMillis();
        String motivo = ("Recorrente: " + PedidoContratadoController.nomeServico(p.getService()));
        motivo = motivo.substring(0, Math.min(80, motivo.length()));
        for (VisitaRecorrente v : visitas.findByPedidoIdOrderByOrdemAsc(p.getId())) {
            long a = quando(LocalDate.parse(v.getData()), hora);
            ids.add(bloqueios.save(new BloqueioAgenda(UUID.randomUUID().toString(), pro.getId(), a, a + DURACAO, motivo, agora)).getId());
        }
        p.setStatus("agendado");
        p.getExtra().put("recorrenteBloqueios", ids);
        return pedidos.save(p);
    }

    /** O cliente não quis quem aceitou: o pedido volta a procurar e a agenda dele é liberada. */
    @Transactional
    public boolean devolverAoMural(String id, String proEmail) {
        Pedido p = pedidos.findById(id).orElse(null);
        if (p == null || !"agendado".equals(p.getStatus()) || p.getProEmail() == null || !p.getProEmail().equalsIgnoreCase(proEmail)) return false;
        liberarAgenda(p);
        p.getExtra().remove("recorrenteBloqueios"); p.getExtra().remove("confirmacaoCliente");
        p.setProEmail(null); p.setProId(null); p.setProfessionalName(null); p.setAcceptedAt(null); p.setStatus("aguardando");
        pedidos.save(p);
        return true;
    }
    private void liberarAgenda(Pedido p) {
        if (!(p.getExtra().get("recorrenteBloqueios") instanceof List<?> ids)) return;
        long agora = System.currentTimeMillis();
        for (Object id : ids) bloqueios.findById(String.valueOf(id)).filter(b -> b.getFim() > agora).ifPresent(bloqueios::delete);
    }

    /* ======================= leitura ======================= */

    @Transactional(readOnly = true)
    public Map<String, Object> ver(String pedidoId, TaskgoPrincipal quem) {
        Pedido p = recorrente(pedidoId);
        boolean cliente = ehCliente(p, quem), pro = ehAtendente(p, quem);
        if (!cliente && !pro) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        List<VisitaRecorrente> lista = visitas.findByPedidoIdOrderByOrdemAsc(pedidoId);
        String hora = hora(p);
        BigDecimal taxa = pagamentos.taxaDe(p);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pedidoId", pedidoId); r.put("status", p.getStatus()); r.put("plano", p.getExtra().get("recorrente")); r.put("taxa", taxa);
        r.put("confirmado", p.getProEmail() != null && !"pendente".equals(p.getExtra().get("confirmacaoCliente")));
        TreeMap<Integer, List<Map<String, Object>>> porSemana = new TreeMap<>();
        for (VisitaRecorrente v : lista) porSemana.computeIfAbsent(v.getSemana(), k -> new ArrayList<>()).add(visitaDto(v, hora, p));
        Integer proxima = proximaSemana(lista);
        List<Map<String, Object>> semanas = new ArrayList<>();
        porSemana.forEach((n, vs) -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("numero", n); s.put("vezes", vs);
            s.put("aPagar", lista.stream().filter(v -> v.getSemana() == n && VisitaRecorrente.PENDENTE.equals(v.getStatus())).map(VisitaRecorrente::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
            s.put("podePagar", !direto() && cliente && n.equals(proxima) && Boolean.TRUE.equals(r.get("confirmado")) && Set.of("agendado", "em_andamento").contains(p.getStatus()));
            semanas.add(s);
        });
        r.put("semanas", semanas); r.put("proximaSemana", direto() ? null : proxima);
        r.put("direto", direto()); r.put("aPagarAgora", soma(lista, Set.of(VisitaRecorrente.A_PAGAR)));
        BigDecimal pago = direto() ? soma(lista, Set.of(VisitaRecorrente.LIBERADA)) : soma(lista, Set.of(VisitaRecorrente.PAGA, VisitaRecorrente.FEITA, VisitaRecorrente.LIBERADA, VisitaRecorrente.CONTESTADA));
        r.put("pago", pago); r.put("liberado", soma(lista, Set.of(VisitaRecorrente.LIBERADA)));
        r.put("feitas", lista.stream().filter(v -> VisitaRecorrente.LIBERADA.equals(v.getStatus()) || VisitaRecorrente.FEITA.equals(v.getStatus())).count());
        r.put("total", lista.stream().map(VisitaRecorrente::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (pro) r.put("recebePorVez", lista.isEmpty() ? BigDecimal.ZERO : lista.get(0).getValor().subtract(lista.get(0).getValor().multiply(taxa).setScale(2, RoundingMode.HALF_UP)));
        return r;
    }
    private static BigDecimal soma(List<VisitaRecorrente> l, Set<String> st) { return l.stream().filter(v -> st.contains(v.getStatus())).map(VisitaRecorrente::getValor).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static Integer proximaSemana(List<VisitaRecorrente> l) { return l.stream().filter(v -> VisitaRecorrente.PENDENTE.equals(v.getStatus())).map(VisitaRecorrente::getSemana).min(Integer::compare).orElse(null); }
    Map<String, Object> visitaDto(VisitaRecorrente v, String hora, Pedido p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId()); m.put("ordem", v.getOrdem()); m.put("data", v.getData()); m.put("quando", quando(LocalDate.parse(v.getData()), hora));
        m.put("semana", v.getSemana()); m.put("valor", v.getValor()); m.put("status", v.getStatus()); m.put("feitaEm", v.getFeitaEm()); m.put("liberarApos", v.getLiberarApos());
        return m;
    }

    /* ======================= pagamento por semana ======================= */

    @Transactional
    public Object pagarSemana(String pedidoId, int semana, TaskgoPrincipal quem, String metodo, String cartaoId) {
        Pedido p = recorrente(pedidoId);
        if (!ehCliente(p, quem)) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        if (!Set.of("agendado", "em_andamento").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "aguardando".equals(p.getStatus()) ? "Espere um profissional aceitar o pedido." : "Este pedido está encerrado.");
        if ("pendente".equals(p.getExtra().get("confirmacaoCliente"))) throw erro(HttpStatus.CONFLICT, "Confirme o profissional antes de pagar.");
        if (direto()) throw erro(HttpStatus.CONFLICT, "Você paga cada vez ao profissional depois que ela acontecer.");
        if ("dinheiro".equals(metodo)) throw erro(HttpStatus.BAD_REQUEST, "O serviço recorrente é pago pela TaskGO: escolha Pix, cartão ou a carteira.");
        List<VisitaRecorrente> lista = visitas.findByPedidoIdOrderByOrdemAsc(pedidoId);
        Integer proxima = proximaSemana(lista);
        if (proxima == null) throw erro(HttpStatus.CONFLICT, "Todas as semanas já foram pagas.");
        if (semana != proxima) throw erro(HttpStatus.CONFLICT, "Pague as semanas na ordem: primeiro a semana " + proxima + ".");
        List<VisitaRecorrente> daSemana = lista.stream().filter(v -> v.getSemana() == semana && VisitaRecorrente.PENDENTE.equals(v.getStatus())).toList();
        BigDecimal valor = daSemana.stream().map(VisitaRecorrente::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        Cobranca c;
        alvo.set(pedidoId + ":" + semana);
        try { c = pagamentos.cobrar(p, quem.id(), metodo, cartaoId, 1, valor, "semana"); } finally { alvo.remove(); }
        for (VisitaRecorrente v : visitas.findByPedidoIdOrderByOrdemAsc(pedidoId))
            if (v.getSemana() == semana && VisitaRecorrente.PENDENTE.equals(v.getStatus()) && !c.getId().equals(v.getCobrancaId())) { v.cobranca(c.getId()); visitas.save(v); }
        if (Cobranca.RECUSADA.equals(c.getStatus())) throw erro(HttpStatus.PAYMENT_REQUIRED, c.getMotivo() == null ? "O cartão não foi aprovado." : c.getMotivo());
        return PagamentoService.CobrancaDto.de(c);
    }

    /** Chamado pelo PagamentoService quando a cobrança de uma semana é confirmada. */
    @Transactional
    public void cobrancaPaga(Cobranca c) {
        long agora = System.currentTimeMillis();
        List<VisitaRecorrente> vs = new ArrayList<>(visitas.findByCobrancaId(c.getId()));
        String a = alvo.get();
        if (vs.isEmpty() && a != null && a.startsWith(c.getPedidoId() + ":")) {
            int semana = Integer.parseInt(a.substring(a.lastIndexOf(':') + 1));
            vs.addAll(visitas.findByPedidoIdOrderByOrdemAsc(c.getPedidoId()).stream().filter(v -> v.getSemana() == semana).toList());
        }
        Pedido p = pedidos.findById(c.getPedidoId()).orElse(null);
        List<VisitaRecorrente> pendentes = vs.stream().filter(v -> VisitaRecorrente.PENDENTE.equals(v.getStatus())).toList();
        BigDecimal valor = pendentes.stream().map(VisitaRecorrente::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (p == null || "cancelado".equals(p.getStatus()) || pendentes.isEmpty()) { devolver(c, c.liquido()); return; }
        for (VisitaRecorrente v : pendentes) { v.pagar(c.getId()); visitas.save(v); }
        if (c.liquido().compareTo(valor) > 0) devolver(c, c.liquido().subtract(valor));
        transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "PAGAMENTO", valor, "RETIDO", p.getId(), agora));
        auditar(c.getClienteId(), "Pagou a semana " + pendentes.get(0).getSemana() + " do serviço recorrente: " + reais(valor), c.getClienteId(), p.getId());
        idDoEmail(p.getProEmail()).ifPresent(pro -> {
            avisar(pro, p.getId(), "Semana paga", "O cliente pagou " + pendentes.size() + (pendentes.size() == 1 ? " vez" : " vezes") + " a partir de " + DM.format(LocalDate.parse(pendentes.get(0).getData())) + ". O valor está garantido com a TaskGO.", "semana-paga:" + c.getId());
            eventos.enviar(pro, "pedido", p.getId());
        });
    }

    /* ======================= cada vez ======================= */

    @Transactional
    public Map<String, Object> agir(String visitaId, TaskgoPrincipal quem, String acao) {
        VisitaRecorrente v = visitas.findById(visitaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Vez não encontrada."));
        Pedido p = recorrente(v.getPedidoId());
        boolean cliente = ehCliente(p, quem), pro = ehAtendente(p, quem);
        if (!cliente && !pro) throw erro(HttpStatus.NOT_FOUND, "Vez não encontrada.");
        if (Set.of("cancelado", "concluido").contains(p.getStatus()) && !"confirmar".equals(acao)) throw erro(HttpStatus.CONFLICT, "Este pedido está encerrado.");
        long agora = System.currentTimeMillis(), inicio = quando(LocalDate.parse(v.getData()), hora(p));
        String dia = DM.format(LocalDate.parse(v.getData()));
        switch (String.valueOf(acao)) {
            case "feita" -> {
                if (!pro) throw erro(HttpStatus.FORBIDDEN, "Só o profissional marca a vez como feita.");
                if (!(direto() ? VisitaRecorrente.PENDENTE : VisitaRecorrente.PAGA).equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, !direto() && VisitaRecorrente.PENDENTE.equals(v.getStatus()) ? "Espere o cliente pagar a semana desta vez." : "Esta vez já foi marcada.");
                if (agora < inicio - HORA) throw erro(HttpStatus.CONFLICT, "Esta vez está marcada para " + dia + ".");
                v.feita(agora, agora + PRAZO_CONFIRMAR);
                if ("agendado".equals(p.getStatus())) { p.setStatus("em_andamento"); if (p.getAcceptedAt() == null) p.setAcceptedAt(agora); pedidos.save(p); }
                avisar(clienteId(p), p.getId(), "Confirme a vez de " + dia, p.getProfessionalName() + (direto() ? " marcou a vez como feita. Confirme e pague " + reais(v.getValor()) + " a ele por Pix (a chave está no pedido)." : " marcou a vez como feita. Confirme, ou o valor vai para ele em 24 horas."), "rec-feita:" + v.getId());
            }
            case "confirmar" -> {
                if (!cliente) throw erro(HttpStatus.FORBIDDEN, "Só o cliente confirma.");
                if (!VisitaRecorrente.FEITA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta vez ainda não foi marcada como feita.");
                if (direto()) { v.aPagar(); idDoEmail(p.getProEmail()).ifPresent(id -> avisar(id, p.getId(), "Vez de " + dia + " confirmada", "O cliente confirmou e vai pagar " + reais(v.getValor()) + " direto a você. Marque quando receber.", "rec-confirmada:" + v.getId())); }
                else liberar(v, p);
            }
            case "recebida" -> {
                if (!pro) throw erro(HttpStatus.FORBIDDEN, "Só o profissional confirma o recebimento.");
                if (!direto() || !VisitaRecorrente.A_PAGAR.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta vez não está esperando pagamento.");
                v.fechar(VisitaRecorrente.LIBERADA, agora);
                if (dinheiro != null) dinheiro.registrarTaxa(quem.id(), p.getId(), "rec:" + v.getId(), v.getValor().multiply(pagamentos.taxaDe(p)));
                avisar(clienteId(p), p.getId(), "Vez de " + dia + " paga", p.getProfessionalName() + " confirmou que recebeu " + reais(v.getValor()) + ".", "rec-recebida:" + v.getId());
            }
            case "nao_veio" -> {
                if (!cliente) throw erro(HttpStatus.FORBIDDEN, "Só o cliente informa que o profissional não veio.");
                if (direto() && VisitaRecorrente.PENDENTE.equals(v.getStatus())) {
                    if (agora < inicio + HORA) throw erro(HttpStatus.CONFLICT, "Espere pelo menos 1 hora depois do horário marcado.");
                    v.fechar(VisitaRecorrente.FALTOU, agora);
                    idDoEmail(p.getProEmail()).ifPresent(id -> avisar(id, p.getId(), "Falta registrada", "O cliente informou que você não foi em " + dia + ".", "rec-faltou:" + v.getId()));
                    if (moderacao != null) idDoEmail(p.getProEmail()).ifPresent(id -> moderacao.registrar(id, "pedido", "Faltou à vez de " + dia + " do serviço recorrente", quem.id(), p.getId()));
                } else if (VisitaRecorrente.PAGA.equals(v.getStatus())) {
                    if (agora < inicio + HORA) throw erro(HttpStatus.CONFLICT, "Espere pelo menos 1 hora depois do horário marcado.");
                    devolverVez(v); v.fechar(VisitaRecorrente.FALTOU, agora);
                    idDoEmail(p.getProEmail()).ifPresent(id -> avisar(id, p.getId(), "Falta registrada", "O cliente informou que você não foi em " + dia + ". O valor desta vez voltou para ele.", "rec-faltou:" + v.getId()));
                    if (moderacao != null) idDoEmail(p.getProEmail()).ifPresent(id -> moderacao.registrar(id, "pedido", "Faltou à vez de " + dia + " do serviço recorrente", quem.id(), p.getId()));
                } else if (VisitaRecorrente.FEITA.equals(v.getStatus())) {
                    v.fechar(VisitaRecorrente.CONTESTADA, agora);
                    avisarEquipe(p.getId(), "Vez contestada", "O cliente diz que o profissional não veio em " + dia + ", mas ele marcou como feita. Decida em Filas.", "rec-contestada:" + v.getId());
                    idDoEmail(p.getProEmail()).ifPresent(id -> avisar(id, p.getId(), "Vez contestada", "O cliente diz que você não foi em " + dia + ". O suporte da TaskGO vai analisar.", "rec-contestada-pro:" + v.getId()));
                } else throw erro(HttpStatus.CONFLICT, "Só dá para informar falta numa vez paga.");
            }
            default -> throw erro(HttpStatus.BAD_REQUEST, "Ação inválida.");
        }
        visitas.save(v);
        talvezEncerrar(p.getId());
        eventos.enviar(clienteId(p), "pedido", p.getId());
        idDoEmail(p.getProEmail()).ifPresent(id -> eventos.enviar(id, "pedido", p.getId()));
        return ver(p.getId(), quem);
    }

    /** Suporte decide uma vez contestada: paga o profissional ou devolve ao cliente. */
    @Transactional
    public Map<String, Object> decidir(String visitaId, boolean aoProfissional, Long equipe) {
        VisitaRecorrente v = visitas.findById(visitaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Vez não encontrada."));
        if (!VisitaRecorrente.CONTESTADA.equals(v.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta vez não está contestada.");
        Pedido p = pedidos.findById(v.getPedidoId()).orElseThrow();
        String dia = DM.format(LocalDate.parse(v.getData()));
        if (aoProfissional && pagamentos.direto()) { v.aPagar(); avisar(clienteId(p), p.getId(), "Pague a vez de " + dia, "O suporte confirmou a vez. Pague " + reais(v.getValor()) + " ao profissional.", "rec-decisao:" + v.getId()); }
        else if (aoProfissional) liberar(v, p);
        else { devolverVez(v); v.fechar(VisitaRecorrente.FALTOU, System.currentTimeMillis()); avisar(clienteId(p), p.getId(), "Vez devolvida", "O suporte devolveu o valor da vez de " + dia + ".", "rec-decisao:" + v.getId()); }
        visitas.save(v);
        auditar(clienteId(p), "Suporte decidiu a vez de " + dia + ": " + (aoProfissional ? "paga ao profissional" : "devolvida ao cliente"), equipe, p.getId());
        talvezEncerrar(p.getId());
        return visitaDto(v, hora(p), p);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> contestadas() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (VisitaRecorrente v : visitas.findByStatus(VisitaRecorrente.CONTESTADA)) pedidos.findById(v.getPedidoId()).ifPresent(p -> {
            Map<String, Object> m = visitaDto(v, hora(p), p);
            m.put("pedidoId", p.getId()); m.put("servico", PedidoContratadoController.nomeServico(p.getService()));
            m.put("cliente", usuarios.findById(clienteId(p) == null ? -1L : clienteId(p)).map(Usuario::getNome).orElse("Cliente"));
            m.put("profissional", p.getProfessionalName());
            r.add(m);
        });
        return r;
    }

    private void liberar(VisitaRecorrente v, Pedido p) {
        BigDecimal taxa = v.getValor().multiply(pagamentos.taxaDe(p)).setScale(2, RoundingMode.HALF_UP);
        creditar(idDoEmail(p.getProEmail()).orElse(null), v.getValor().subtract(taxa), p.getId(), "Vez de " + DM.format(LocalDate.parse(v.getData())) + " paga", "rec-repasse:" + v.getId());
        v.fechar(VisitaRecorrente.LIBERADA, System.currentTimeMillis());
    }
    private void devolverVez(VisitaRecorrente v) {
        pagamentos.cobranca(v.getCobrancaId()).filter(c -> Cobranca.PAGA.equals(c.getStatus())).ifPresent(c -> devolver(c, v.getValor()));
    }
    /* Devolve pelo provedor; se ele ainda não aceitar, a devolução entra na fila (ObraService.prazos repete a cada minuto). */
    private void devolver(Cobranca c, BigDecimal valor) {
        if (valor.signum() <= 0) return;
        try { pagamentos.devolverCobranca(c, valor); }
        catch (PagamentoService.EstornoAdiado adiado) {
            transacoes.save(new TransacaoFinanceira(UUID.randomUUID().toString(), c.getClienteId(), "DEVOLUCAO_PENDENTE", valor, "PENDENTE", c.getId(), System.currentTimeMillis()));
            avisar(c.getClienteId(), c.getPedidoId(), "Devolução em andamento", reais(valor) + " voltam para você assim que o banco liberar.", "devolucao-pendente:" + c.getId() + ":" + valor + ":" + System.nanoTime());
        }
    }

    /* Todas as vezes fechadas: o pedido termina (concluído se alguma foi feita). */
    private void talvezEncerrar(String pedidoId) {
        List<VisitaRecorrente> l = visitas.findByPedidoIdOrderByOrdemAsc(pedidoId);
        if (l.isEmpty() || l.stream().anyMatch(v -> Set.of(VisitaRecorrente.PENDENTE, VisitaRecorrente.PAGA, VisitaRecorrente.FEITA, VisitaRecorrente.A_PAGAR, VisitaRecorrente.CONTESTADA).contains(v.getStatus()))) return;
        Pedido p = pedidos.findById(pedidoId).orElseThrow();
        if (Set.of("cancelado", "concluido").contains(p.getStatus())) return;
        boolean feita = l.stream().anyMatch(v -> VisitaRecorrente.LIBERADA.equals(v.getStatus()));
        p.setStatus(feita ? "concluido" : "cancelado");
        if (feita) p.setCompletedAt(System.currentTimeMillis()); else p.setCancelReason("Nenhuma vez foi feita");
        p.setProgress(feita ? 1.0 : p.getProgress());
        pedidos.save(p);
        liberarAgenda(p);
        if (feita) avisar(clienteId(p), p.getId(), "Serviço recorrente concluído", "Todas as vezes foram fechadas. Avalie o profissional.", "rec-concluido:" + p.getId());
    }

    /* ======================= cancelamento ======================= */

    /** Cancelar: pagas e não feitas voltam; feitas vão para o profissional; contestadas ficam com o suporte. */
    @Transactional
    public Pedido cancelar(String pedidoId, TaskgoPrincipal quem, String motivo) {
        Pedido p = recorrente(pedidoId);
        boolean cliente = ehCliente(p, quem), atende = ehAtendente(p, quem);
        if (!cliente && !atende) throw erro(HttpStatus.FORBIDDEN, "Este pedido não pertence a esta conta.");
        if (Set.of("cancelado", "concluido").contains(p.getStatus())) throw erro(HttpStatus.CONFLICT, "Este pedido já está encerrado.");
        long agora = System.currentTimeMillis();
        BigDecimal devolvido = BigDecimal.ZERO;
        for (VisitaRecorrente v : visitas.findByPedidoIdOrderByOrdemAsc(pedidoId)) {
            switch (v.getStatus()) {
                case VisitaRecorrente.PENDENTE -> {
                    pagamentos.cobranca(v.getCobrancaId()).filter(c -> Cobranca.PENDENTE.equals(c.getStatus())).ifPresent(pagamentos::cancelarAberta);
                    v.fechar(VisitaRecorrente.CANCELADA, agora);
                }
                case VisitaRecorrente.PAGA -> { devolverVez(v); devolvido = devolvido.add(v.getValor()); v.fechar(VisitaRecorrente.DEVOLVIDA, agora); }
                case VisitaRecorrente.FEITA -> { if (direto()) v.aPagar(); else liberar(v, p); }
                default -> { }
            }
            visitas.save(v);
        }
        Pedido atual = pedidos.findById(pedidoId).orElseThrow();
        atual.setStatus("cancelado");
        String m = motivo == null || motivo.isBlank() ? (cliente ? "Cliente cancelou o serviço recorrente" : "Profissional cancelou o serviço recorrente") : motivo.trim();
        atual.setCancelReason(m.substring(0, Math.min(200, m.length())));
        pedidos.save(atual);
        liberarAgenda(atual);
        Long outro = cliente ? idDoEmail(atual.getProEmail()).orElse(null) : clienteId(atual);
        avisar(outro, pedidoId, "Serviço recorrente cancelado", (cliente ? "O cliente" : "O profissional") + " cancelou as próximas vezes." + (!cliente && devolvido.signum() > 0 ? " " + reais(devolvido) + " voltam para você." : ""), "rec-cancelado:" + pedidoId + ":" + outro);
        if (outro != null) eventos.enviar(outro, "pedido", pedidoId);
        auditar(quem.id(), "Cancelou o serviço recorrente", quem.id(), pedidoId);
        return atual;
    }

    /* ======================= prazos (a cada minuto) ======================= */

    @Scheduled(fixedDelay = 60_000, initialDelay = 50_000)
    @Transactional
    public void prazos() {
        long agora = System.currentTimeMillis();
        /* Feita e não confirmada em 24 h: vai para o profissional. */
        for (VisitaRecorrente v : visitas.findByStatus(VisitaRecorrente.FEITA))
            if (v.getLiberarApos() != null && v.getLiberarApos() <= agora) pedidos.findById(v.getPedidoId()).ifPresent(p -> {
                if (direto()) { v.aPagar(); avisar(clienteId(p), p.getId(), "Pague a vez de " + DM.format(LocalDate.parse(v.getData())), "A vez foi confirmada. Pague " + reais(v.getValor()) + " ao profissional.", "rec-auto:" + v.getId()); }
                else liberar(v, p);
                visitas.save(v); talvezEncerrar(p.getId()); });
        LocalDate hoje = LocalDate.now(FUSO);
        for (VisitaRecorrente v : visitas.findByStatus(VisitaRecorrente.PENDENTE)) {
            Pedido p = pedidos.findById(v.getPedidoId()).orElse(null);
            if (p == null) continue;
            LocalDate d = LocalDate.parse(v.getData());
            /* Ninguém aceitou até o dia: a vez não acontece. Semana sem pagamento no dia: a vez é cancelada. */
            if (direto() ? d.isBefore(hoje.minusDays(1)) : d.isBefore(hoje)) {
                pagamentos.cobranca(v.getCobrancaId()).filter(c -> Cobranca.PENDENTE.equals(c.getStatus())).ifPresent(pagamentos::cancelarAberta);
                v.fechar(VisitaRecorrente.CANCELADA, agora); visitas.save(v);
                if (p.getProEmail() != null) avisar(clienteId(p), p.getId(), "Vez de " + DM.format(d) + " cancelada", direto() ? "O profissional não marcou esta vez como feita." : "A semana não foi paga a tempo.", "rec-sem-pagar:" + v.getId());
                talvezEncerrar(p.getId());
            } else if (!direto() && p.getProEmail() != null && !"pendente".equals(p.getExtra().get("confirmacaoCliente")) && !d.isAfter(hoje.plusDays(2)))
                avisar(clienteId(p), p.getId(), "Pague a semana " + v.getSemana(), "A próxima vez é em " + DM.format(d) + ". Pague a semana para o profissional ir.", "rec-lembrete:" + p.getId() + ":" + v.getSemana());
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
    private Pedido recorrente(String id) {
        Pedido p = pedidos.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!eh(p)) throw erro(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        return p;
    }
    static String hora(Pedido p) { return p.getExtra().get("recorrente") instanceof Map<?, ?> m && m.get("hora") != null ? String.valueOf(m.get("hora")) : "08:00"; }
    private static boolean ehCliente(Pedido p, TaskgoPrincipal q) { return String.valueOf(q.id()).equals(p.getClientId()) || q.email().equalsIgnoreCase(String.valueOf(p.getClientEmail())); }
    private static boolean ehAtendente(Pedido p, TaskgoPrincipal q) { return p.getProEmail() != null && q.email().equalsIgnoreCase(p.getProEmail()); }
    private static Long clienteId(Pedido p) { try { return Long.valueOf(p.getClientId()); } catch (RuntimeException e) { return null; } }
    private java.util.Optional<Long> idDoEmail(String email) { return email == null ? java.util.Optional.empty() : usuarios.findByEmailIgnoreCase(email).map(Usuario::getId); }
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
