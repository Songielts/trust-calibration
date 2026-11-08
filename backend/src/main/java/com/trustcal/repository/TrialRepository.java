package com.trustcal.repository;

import com.trustcal.entity.Participant;
import com.trustcal.entity.Trial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrialRepository extends JpaRepository<Trial, Long> {
    Optional<Trial> findByParticipantAndTrialIndex(Participant participant, Integer trialIndex);

    List<Trial> findByParticipantOrderByTrialIndexAsc(Participant participant);

    long countByParticipantAndCompletedTrue(Participant participant);

    @Query("select t from Trial t join fetch t.participant p join fetch t.task where t.completed = true order by p.id, t.trialIndex")
    List<Trial> findAllCompletedWithDetails();

    void deleteByParticipantIn(List<Participant> participants);
}
