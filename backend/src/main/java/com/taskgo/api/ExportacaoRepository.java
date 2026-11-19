package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExportacaoRepository extends JpaRepository<Exportacao, String> {
    /* Lista sem o arquivo (só os dados leves). */
    @Query("select new com.taskgo.api.ExportacaoRepository$Resumo(e.id, e.tipo, e.de, e.ate, e.status, e.linhas, e.erro, e.criadaEm, e.prontaEm) from Exportacao e where e.autorId = :autor order by e.criadaEm desc")
    List<Resumo> doAutor(@Param("autor") Long autor, org.springframework.data.domain.Pageable pagina);
    long countByAutorIdAndStatus(Long autorId, String status);
    @Modifying @Query("delete from Exportacao e where e.criadaEm < :limite")
    int apagarAntigas(@Param("limite") long limite);

    record Resumo(String id, String tipo, java.time.LocalDate de, java.time.LocalDate ate, String status, long linhas, String erro, long criadaEm, Long prontaEm) {}
}
