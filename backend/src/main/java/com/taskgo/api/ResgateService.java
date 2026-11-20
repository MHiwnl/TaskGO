package com.taskgo.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/* Resgate do profissional: o saldo da carteira vai para a chave Pix dele.
   Asaas: transferência Pix de verdade (sandbox ou produção), conferida por uma rotina; se falhar, o valor volta ao saldo.
   Modo simulado: conclui na hora. */
@Service
public class ResgateService {
    static final Set<String> TIPOS = Set.of("CPF", "CNPJ", "EMAIL", "PHONE", "EVP");
    private final SolicitacaoSaqueRepository saques;
    private final CarteiraRepository carteiras;
    private final UsuarioRepository usuarios;
    private final TransacaoFinanceiraRepository transacoes;
    private final PagamentoService pagamentos;
    private final AsaasCliente asaas;
    private final NotificacaoService notificacoes;

    public ResgateService(SolicitacaoSaqueRepository saques, CarteiraRepository carteiras, UsuarioRepository usuarios, TransacaoFinanceiraRepository transacoes,
                          PagamentoService pagamentos, AsaasCliente asaas, NotificacaoService notificacoes) {
        this.saques = saques; this.carteiras = carteiras; this.usuarios = usuarios; this.transacoes = transacoes;
        this.pagamentos = pagamentos; this.asaas = asaas; this.notificacoes = notificacoes;
    }

    /* ---------- chave Pix do profissional ---------- */
    public record ChavePix(String tipo, String chave) {}
    static ChavePix chave(Usuario u) {
        return u.getExtra().get("chavePix") instanceof Map<?, ?> m && m.get("chave") instanceof String c ? new ChavePix(String.valueOf(m.get("tipo")), c) : null;
    }
    @Transactional
    static boolean cpfValido(String d) {
        if (d.length() != 11 || d.chars().distinct().count() == 1) return false;
        for (int n = 9; n <= 10; n++) {
            int soma = 0; for (int i = 0; i < n; i++) soma += (d.charAt(i) - '0') * (n + 1 - i);
            if (soma * 10 % 11 % 10 != d.charAt(n) - '0') return false;
        }
        return true;
    }
    static boolean cnpjValido(String d) {
        if (d.length() != 14 || d.chars().distinct().count() == 1) return false;
        int[][] pesos = {{5,4,3,2,9,8,7,6,5,4,3,2}, {6,5,4,3,2,9,8,7,6,5,4,3,2}};
        for (int[] p : pesos) {
            int soma = 0; for (int i = 0; i < p.length; i++) soma += (d.charAt(i) - '0') * p[i];
            int r = soma % 11; if ((r < 2 ? 0 : 11 - r) != d.charAt(p.length) - '0') return false;
        }
        return true;
    }
    /** Confere e põe no formato do Pix uma chave (CPF, CNPJ, e-mail, telefone ou aleatória). */
    public static ChavePix normalizarChave(String tipo, String chave) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT), c = chave == null ? "" : chave.trim();
        if (!TIPOS.contains(t)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha o tipo da chave: CPF, CNPJ, e-mail, telefone ou aleatória.");
        String digitos = c.replaceAll("\\D", "");
        boolean valida = switch (t) {
            case "CPF" -> c.matches("^[0-9.\\-\\s]+$") && cpfValido(digitos);
            case "CNPJ" -> c.matches("^[0-9./\\-\\s]+$") && cnpjValido(digitos);
            case "EMAIL" -> c.length() <= 77 && c.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}$");
            case "PHONE" -> c.matches("^[0-9()+\\-\\s]+$") && (digitos.length() == 11 || digitos.length() == 13 && digitos.startsWith("55")) && digitos.charAt(digitos.length() - 9) == '9';
            default -> c.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        };
        if (!valida) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chave Pix inválida para esse tipo.");
        String normal = switch (t) {
            case "CPF", "CNPJ" -> digitos;
            case "PHONE" -> "+" + (digitos.startsWith("55") ? digitos : "55" + digitos);
            default -> c.toLowerCase(Locale.ROOT);
        };
        return new ChavePix(t, normal);
    }
    public ChavePix salvarChave(Long usuarioId, String tipo, String chave) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!"profissional".equalsIgnoreCase(u.getTipo()) && !AjudaRapida.conhece(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só profissionais e quem faz ajuda rápida cadastram chave Pix.");
        ChavePix k = normalizarChave(tipo, chave);
        String t = k.tipo(), normal = k.chave();
        u.getExtra().put("chavePix", Map.of("tipo", t, "chave", normal));
        usuarios.save(u);
        return new ChavePix(t, normal);
    }
    @Transactional(readOnly = true)
    public ChavePix chaveDe(Long usuarioId) { return usuarios.findById(usuarioId).map(ResgateService::chave).orElse(null); }

    /* Depois do pedido de resgate (valor já reservado): envia a transferência. */
    @Transactional
    public void enviar(String saqueId) {
        SolicitacaoSaque s = saques.findById(saqueId).orElse(null);
        if (s == null || !"SOLICITADO".equals(s.getStatus())) return;
        Usuario u = usuarios.findById(s.getUsuarioId()).orElse(null);
        ChavePix k = u == null ? null : chave(u);
        if (pagamentos.asaasAtivo()) {
            if (k == null) { falhou(s, "Cadastre a chave Pix na Carteira antes de resgatar."); return; }
            try {
                JsonNode t = asaas.transferirPix(s.getValor(), k.chave(), k.tipo(), "Resgate TaskGO");
                s.enviada(t.path("id").asText(), k.tipo() + ":" + k.chave());
                saques.save(s);
                conferir(s);
            } catch (AsaasCliente.Erro e) {
                /* Sem resposta (503: tempo esgotado, rede) a transferência pode ter saído: o valor fica reservado e a equipe confere no Asaas.
                   Só uma recusa do Asaas (4xx) devolve o saldo. */
                if (e.status == 503) verificar(s); else falhou(s, e.getMessage());
            }
        } else if (pagamentos.simulado()) {
            s.enviada("sim_" + UUID.randomUUID(), k == null ? null : k.tipo() + ":" + k.chave());
            concluir(s);
        }
    }

    /* A cada 2 minutos confere as transferências em andamento. */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void conferirPendentes() {
        if (!pagamentos.asaasAtivo()) return;
        for (SolicitacaoSaque s : saques.findByStatus("PROCESSANDO")) {
            try { conferirSeparado(s.getId()); } catch (RuntimeException ignorar) { /* tenta de novo na próxima volta */ }
        }
    }
    @Transactional
    public void conferirSeparado(String id) { saques.findById(id).ifPresent(this::conferir); }

    private void conferir(SolicitacaoSaque s) {
        if (!"PROCESSANDO".equals(s.getStatus()) || s.getTransferenciaId() == null) return;
        String st;
        try { st = asaas.transferencia(s.getTransferenciaId()).path("status").asText(); } catch (AsaasCliente.Erro e) { return; }
        if ("DONE".equals(st)) concluir(s);
        else if ("FAILED".equals(st) || "CANCELLED".equals(st)) falhou(s, "A transferência foi recusada pelo banco. Confira a chave Pix.");
    }
    private void concluir(SolicitacaoSaque s) {
        carteiras.findByUsuarioIdForUpdate(s.getUsuarioId()).ifPresent(c -> { c.concluirReserva(s.getValor()); carteiras.save(c); });
        s.situacao("CONCLUIDO"); saques.save(s);
        fecharMovimento(s, "CONCLUIDA");
        avisar(s.getUsuarioId(), "Resgate enviado", PagamentoService.reais(s.getValor()) + " foram para a sua chave Pix.", "resgate:" + s.getId());
    }
    /* A equipe conferiu no Asaas um resgate sem resposta: saiu (conclui) ou não saiu (devolve o saldo). */
    @Transactional
    public SolicitacaoSaque resolverVerificar(String id, boolean saiu) {
        SolicitacaoSaque s = saques.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Resgate não encontrado."));
        if (!"VERIFICAR".equals(s.getStatus())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Este resgate não está em conferência.");
        if (saiu) concluir(s); else falhou(s, "A transferência não saiu. O valor voltou para o seu saldo.");
        return s;
    }
    private void verificar(SolicitacaoSaque s) {
        s.situacao("VERIFICAR"); saques.save(s);
        org.slf4j.LoggerFactory.getLogger(ResgateService.class).error("Resgate {} sem resposta do Asaas: conferir no painel antes de devolver ou concluir.", s.getId());
        avisar(s.getUsuarioId(), "Resgate em conferência", "O banco demorou a responder. Estamos conferindo o envio de " + PagamentoService.reais(s.getValor()) + "; o valor continua reservado.", "resgate-verificar:" + s.getId());
    }
    private void falhou(SolicitacaoSaque s, String motivo) {
        carteiras.findByUsuarioIdForUpdate(s.getUsuarioId()).ifPresent(c -> { c.devolverReserva(s.getValor()); carteiras.save(c); });
        s.situacao("FALHOU"); saques.save(s);
        fecharMovimento(s, "FALHOU");
        avisar(s.getUsuarioId(), "Resgate não enviado", motivo + " O valor voltou para o seu saldo.", "resgate-falhou:" + s.getId());
    }
    /* A linha do extrato criada ao pedir o resgate passa a mostrar como terminou. */
    private void fecharMovimento(SolicitacaoSaque s, String status) {
        transacoes.findByReferenciaAndTipo(s.getId(), "SAQUE_RESERVADO").forEach(t -> { t.situacao(status); transacoes.save(t); });
    }
    private void avisar(Long u, String titulo, String texto, String chave) {
        try { notificacoes.criar(u, null, "conta", titulo, texto, chave); } catch (RuntimeException ignorar) { /* aviso repetido */ }
    }
}
