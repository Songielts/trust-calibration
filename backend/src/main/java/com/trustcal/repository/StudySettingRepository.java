package com.trustcal.repository;

import com.trustcal.entity.StudySetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySettingRepository extends JpaRepository<StudySetting, String> {
}
