package com.trustcal.repository;

import com.trustcal.entity.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {
    List<EvaluationRun> findAllByOrderByIdDesc();

    List<EvaluationRun> findByStatus(EvaluationRun.Status status);
}
