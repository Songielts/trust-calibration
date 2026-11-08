package com.trustcal.repository;

import com.trustcal.entity.Participant;
import com.trustcal.entity.PostSurvey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PostSurveyRepository extends JpaRepository<PostSurvey, Long> {
    Optional<PostSurvey> findByParticipant(Participant participant);

    boolean existsByParticipant(Participant participant);

    @Query("select s from PostSurvey s join fetch s.participant")
    List<PostSurvey> findAllWithParticipant();

    void deleteByParticipantIn(List<Participant> participants);
}
