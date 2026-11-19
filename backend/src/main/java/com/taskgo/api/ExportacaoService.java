package com.taskgo.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* Exportação em CSV feita em segundo plano, com período obrigatório (até 1 ano) e no máximo 500 mil linhas.
   Lê o banco em páginas de 1000, nunca tudo de uma vez. Avisa quem pediu quando fica pronta; o arquivo some em 7 dias. */
@Service
public class ExportacaoService {
    static final Set<String> TIPOS = Set.of("pedidos", "contas", "auditoria");
    static final int PAGINA = 1000, MAX_LINHAS = 500_000;
    private final ExportacaoRepository exportacoes;
    private final PedidoContratadoRepository pedidos;
    private final UsuarioRepository usuarios;
    private final HistoricoContaRepository historico;
    private final NotificacaoService notificacoes;
    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "exportacoes"); t.setDaemon(true); return t; });

    public ExportacaoService(ExportacaoRepository exportacoes, PedidoContratadoRepository pedidos, UsuarioRepository usuarios,
                             HistoricoContaRepository historico, NotificacaoService notificacoes) {
        this.exportacoes = exportacoes; this.pedidos = pedidos; this.usuarios = usuarios; this.historico = historico; this.notificacoes = notificacoes;
    }

    @Transactional
    public Exportacao pedir(Long autor, String tipo, LocalDate de, LocalDate ate) {
        if (!TIPOS.contains(tipo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha pedidos, contas ou auditoria.");
        if (de == null || ate == null || ate.isBefore(de)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o período: a data inicial precisa ser antes da final.");
        if (ChronoUnit.DAYS.between(de, ate) > 366) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exporte no máximo 1 ano de cada vez.");
        if (exportacoes.countByAutorIdAndStatus(autor, Exportacao.GERANDO) >= 2) throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já tem 2 planilhas sendo geradas. Espere uma ficar pronta.");
        Exportacao e = exportacoes.save(new Exportacao(UUID.randomUUID().toString(), autor, tipo, de, ate, System.currentTimeMillis()));
        String id = e.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { fila.submit(() -> gerar(id)); }
        });
        return e;
    }

    void gerar(String id) {
        Exportacao e = exportacoes.findById(id).orElse(null);
        if (e == null) return;
        long a = MetricasService.inicio(e.getDe()), b = MetricasService.inicio(e.getAte().plusDays(1));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); /* BOM: o Excel abre com acentos certos */
            long linhas = 0;
            switch (e.getTipo()) {
                case "pedidos" -> {
                    linha(out, "id", "criado_em", "servico", "status", "valor", "pagamento", "bairro", "cliente_email", "profissional_email", "aceito_em", "concluido_em", "nota");
                    for (int pg = 0; linhas < MAX_LINHAS; pg++) {
                        List<Pedido> lote = pedidos.paginaEntre(a, b, PageRequest.of(pg, PAGINA));
                        for (Pedido p : lote) { linha(out, p.getId(), data(p.getCreated()), p.getService(), p.getStatus(), p.getPrice(), p.getPaid(), p.getExtra().get("bairro"),
                                p.getClientEmail(), p.getProEmail(), data(p.getAcceptedAt()), data(p.getCompletedAt()), p.getRating()); linhas++; }
                        if (lote.size() < PAGINA) break;
                    }
                }
                case "contas" -> {
                    linha(out, "id", "criada_em", "tipo", "nome", "email", "cidade", "categoria", "verificada");
                    for (int pg = 0; linhas < MAX_LINHAS; pg++) {
                        List<Usuario> lote = usuarios.paginaEntre(a, b, PageRequest.of(pg, PAGINA));
                        for (Usuario u : lote) { linha(out, u.getId(), data(u.getCriadoEm()), u.getTipo(), u.getNome(), u.getEmail(), u.getCidade(), u.getService(), VerificacaoController.verificado(u)); linhas++; }
                        if (lote.size() < PAGINA) break;
                    }
                }
                default -> {
                    linha(out, "quando", "tipo", "conta_id", "autor_id", "pedido_id", "texto");
                    for (int pg = 0; linhas < MAX_LINHAS; pg++) {
                        List<HistoricoConta> lote = historico.paginaEntre(a, b, PageRequest.of(pg, PAGINA));
                        for (HistoricoConta h : lote) { linha(out, data(h.getCriadoEm()), h.getTipo(), h.getUsuarioId(), h.getAutorId(), h.getPedidoId(), h.getTexto()); linhas++; }
                        if (lote.size() < PAGINA) break;
                    }
                }
            }
            e.pronta(out.toByteArray(), linhas, System.currentTimeMillis());
            exportacoes.save(e);
            avisar(e, "Planilha pronta", "A planilha de " + e.getTipo() + " (" + e.getDe() + " a " + e.getAte() + ") tem " + linhas + " linhas" + (linhas >= MAX_LINHAS ? ", o limite; diminua o período para ver o resto" : "") + ". Baixe em Exportar.");
        } catch (Exception erro) {
            e.falhou(erro.getMessage(), System.currentTimeMillis());
            exportacoes.save(e);
            avisar(e, "Planilha não gerada", "Não foi possível gerar a planilha de " + e.getTipo() + ". Tente de novo.");
        }
    }

    private void avisar(Exportacao e, String titulo, String texto) {
        try { notificacoes.criar(e.getAutorId(), null, "conta", titulo, texto, "exportacao:" + e.getId()); } catch (RuntimeException ignorar) { /* sem aviso */ }
    }
    private static String data(Long ms) { return ms == null ? "" : java.time.Instant.ofEpochMilli(ms).atZone(MetricasService.FUSO).toLocalDateTime().withNano(0).toString().replace('T', ' '); }
    /* Uma linha de CSV (;) com aspas; célula que começa com = + - @ ganha ' na frente (evita fórmula ao abrir no Excel). */
    static void linha(ByteArrayOutputStream out, Object... colunas) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < colunas.length; i++) {
            if (i > 0) s.append(';');
            String v = colunas[i] == null ? "" : String.valueOf(colunas[i]);
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
            s.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        s.append("\r\n");
        out.writeBytes(s.toString().getBytes(StandardCharsets.UTF_8));
    }

    /* Limpeza diária: planilhas com mais de 7 dias e eventos de rotina da auditoria com mais de 12 meses. */
    @Scheduled(cron = "0 40 3 * * *", zone = "America/Recife")
    @Transactional
    public void limpar() {
        long agora = System.currentTimeMillis();
        exportacoes.apagarAntigas(agora - 7L * 24 * 60 * 60 * 1000);
        historico.apagarAntigos(agora - 365L * 24 * 60 * 60 * 1000, Set.of("pedido", "acesso", "pagamento"));
    }
}
