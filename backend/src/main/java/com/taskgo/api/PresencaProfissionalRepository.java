package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresencaProfissionalRepository extends JpaRepository<PresencaProfissional, Long> {
    List<PresencaProfissional> findByAtualizadaEmGreaterThan(long desde);
}
