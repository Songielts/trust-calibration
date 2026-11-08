package com.trustcal.repository;

import com.trustcal.entity.ExperimentCondition;
import com.trustcal.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    Optional<Participant> findByPublicId(String publicId);

    /** Used for balanced assignment of real participants (excludes simulated and demo sessions). */
    long countByConditionAndSimulatedAndDemo(ExperimentCondition condition, boolean simulated, boolean demo);

    List<Participant> findAllByOrderByCreatedAtDesc();

    List<Participant> findBySimulatedFalseAndDemoFalseOrderByCreatedAtDesc();

    List<Participant> findBySimulatedTrue();

    List<Participant> findByDemoTrue();
    List<Participant> findByAgeGroup(String ageGroup);
    List<Participant> findByEducation(String education);

}
