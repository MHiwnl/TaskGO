package com.taskgo.api;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Números das bolinhas das abas: mensagens não lidas e, para o profissional online, os pedidos abertos da categoria.
   Os ids das oportunidades vão junto para o site marcar quais a pessoa já viu. */
@RestController
public class ContadoresController {
    private final UsuarioRepository usuarios;
    private final MensagemRepository mensagens;
    private final PedidoContratadoRepository pedidos;

    public ContadoresController(UsuarioRepository usuarios, MensagemRepository mensagens, PedidoContratadoRepository pedidos) {
        this.usuarios = usuarios; this.mensagens = mensagens; this.pedidos = pedidos;
    }

    private ServicosProfissional servicosPro;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setServicosPro(ServicosProfissional s) { this.servicosPro = s; }
    private ExigenciaVerificacao exigencia;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }

    @GetMapping("/api/conta/contadores")
    @Transactional(readOnly = true)
    public Map<String, Object> contadores(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Usuario u = usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        long naoLidas = mensagens.countByDestinatarioIdAndLidaEmIsNull(u.getId());
        List<String> oportunidades = List.of();
        if ("profissional".equalsIgnoreCase(u.getTipo()) && DisponibilidadeController.online(u) && u.getService() != null && (exigencia == null || exigencia.liberado(u)))
            oportunidades = pedidos.encontrarDisponiveisPorServicos("aguardando", servicosPro == null ? List.of(u.getService()) : servicosPro.de(u), PageRequest.of(0, 300, Sort.by(Sort.Direction.DESC, "created")))
                    .map(Pedido::getId).getContent();
        return Map.of("mensagensNaoLidas", naoLidas, "oportunidades", oportunidades);
    }
}
