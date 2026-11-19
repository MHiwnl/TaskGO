package com.taskgo.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profissionais")
public class ProfissionalController {
    private final UsuarioRepository usuarios;
    private final PedidoContratadoRepository pedidos;

    public ProfissionalController(UsuarioRepository usuarios, PedidoContratadoRepository pedidos) { this.usuarios = usuarios; this.pedidos = pedidos; }
    private ExigenciaVerificacao exigencia;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setExigencia(ExigenciaVerificacao e) { this.exigencia = e; }
    private PlanoService planos;
    @org.springframework.beans.factory.annotation.Autowired(required = false) void setPlanos(PlanoService p) { this.planos = p; }

    @GetMapping
    public List<ProfissionalPublicoDto> listar(@RequestParam(required = false) String servico) {
        List<Usuario> profissionais = servico == null || servico.isBlank()
                ? usuarios.findByTipoIgnoreCaseOrderByNomeAsc("profissional")
                : usuarios.profissionaisDoServico(servico.trim());
        /* Só aparece na lista quem pode receber pedidos (identidade verificada). */
        List<Usuario> liberados = profissionais.stream().filter(p -> p.getProId() != null && !p.getProId().isBlank()).filter(p -> exigencia == null || exigencia.liberado(p)).toList();
        java.util.Set<Long> pros = planos == null ? java.util.Set.of() : planos.pros(liberados.stream().map(Usuario::getId).toList());
        /* Plano Pro: aparece primeiro; dentro de cada grupo, a ordem de sempre. */
        return liberados.stream().sorted(java.util.Comparator.comparing((Usuario p) -> !pros.contains(p.getId())))
                .map(p -> { Object[] summary = resumo(pedidos.resumoAvaliacoes(p.getEmail()));
                    Double rating = summary[0] instanceof Number n ? n.doubleValue() : null;
                    long reviews = summary[1] instanceof Number n ? n.longValue() : 0L;
                    return ProfissionalPublicoDto.from(p, rating, reviews, pros.contains(p.getId())); }).toList();
    }

    /* Uma consulta de uma linha com duas colunas chega do Spring Data como [[média, total]], não como [média, total].
       Sem desembrulhar, summary[1] estourava (HTTP 500) assim que existia um profissional cadastrado. */
    static Object[] resumo(Object[] bruto) {
        if (bruto == null || bruto.length == 0) return new Object[]{null, 0L};
        if (bruto.length == 1 && bruto[0] instanceof Object[] linha) return linha.length >= 2 ? linha : new Object[]{null, 0L};
        return bruto.length >= 2 ? bruto : new Object[]{null, 0L};
    }
}
