package com.trustcal.repository;

import com.trustcal.entity.DecisionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionTaskRepository extends JpaRepository<DecisionTask, Long> {
    List<DecisionTask> findAllByOrderByDisplayOrderAsc();
}
