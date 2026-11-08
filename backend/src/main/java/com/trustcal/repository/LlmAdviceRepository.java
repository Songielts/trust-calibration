package com.trustcal.repository;

import com.trustcal.entity.DecisionTask;
import com.trustcal.entity.LlmAdvice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LlmAdviceRepository extends JpaRepository<LlmAdvice, Long> {
    @Query("select a from LlmAdvice a join fetch a.task where a.runId = ?1 order by a.id")
    List<LlmAdvice> findByRunIdWithTask(Long runId);

    long countByRunId(Long runId);

    @Query("select a from LlmAdvice a join fetch a.task where a.frozen = true")
    List<LlmAdvice> findFrozenWithTask();

    Optional<LlmAdvice> findFirstByTaskAndFrozenTrue(DecisionTask task);

    @Query("select a from LlmAdvice a join fetch a.task order by a.id")
    List<LlmAdvice> findAllWithTask();
}
