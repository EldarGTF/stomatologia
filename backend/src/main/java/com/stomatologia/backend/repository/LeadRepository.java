package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    long countByStatusIn(Collection<LeadStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndStatus(LocalDateTime from, LeadStatus status);
}
