package com.taskgo.api;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricaDiariaRepository extends JpaRepository<MetricaDiaria, LocalDate> {
    List<MetricaDiaria> findByDiaBetweenOrderByDiaAsc(LocalDate de, LocalDate ate);
}
