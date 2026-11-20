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
    private final RankingProfissionais ranking;

    public ProfissionalController(UsuarioRepository usuarios, RankingProfissionais ranking) { this.usuarios = usuarios; this.ranking = ranking; }
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
        /* Plano Pro aparece primeiro; dentro de cada grupo, pela pontuação de qualidade e depois pelo nome. Sem lat/lng: rota pública. */
        if (liberados.isEmpty()) return List.of();
        java.util.Map<Long, RankingProfissionais.Metricas> qualidade = ranking.metricas(liberados);
        java.util.Comparator<Usuario> ordem = java.util.Comparator.comparing((Usuario p) -> !pros.contains(p.getId()))
                .thenComparingDouble(p -> -RankingProfissionais.pontuacaoLista(qualidade.get(p.getId())))
                .thenComparing(Usuario::getNome, java.util.Comparator.<String>nullsLast(java.util.Comparator.<String>naturalOrder()));
        return liberados.stream().sorted(ordem)
                .map(p -> { RankingProfissionais.Metricas m = qualidade.get(p.getId());
                    return ProfissionalPublicoDto.from(p, m.media(), m.avaliacoes(), pros.contains(p.getId())); }).toList();
    }

    /* Uma consulta de uma linha com duas colunas chega do Spring Data como [[média, total]], não como [média, total].
       Sem desembrulhar, summary[1] estourava (HTTP 500) assim que existia um profissional cadastrado. */
    static Object[] resumo(Object[] bruto) {
        if (bruto == null || bruto.length == 0) return new Object[]{null, 0L};
        if (bruto.length == 1 && bruto[0] instanceof Object[] linha) return linha.length >= 2 ? linha : new Object[]{null, 0L};
        return bruto.length >= 2 ? bruto : new Object[]{null, 0L};
    }
}
