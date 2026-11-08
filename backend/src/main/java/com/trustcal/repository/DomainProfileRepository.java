package com.trustcal.repository;

import com.trustcal.entity.DomainProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomainProfileRepository extends JpaRepository<DomainProfile, Long> {
    Optional<DomainProfile> findByCode(String code);

    List<DomainProfile> findAllByOrderByDisplayOrderAsc();
}
