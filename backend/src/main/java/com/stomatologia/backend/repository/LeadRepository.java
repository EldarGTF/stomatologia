package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    long countByStatusIn(Collection<LeadStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndStatus(LocalDateTime from, LeadStatus status);

    long countByStatusAndConfirmedAtIsNull(LeadStatus status);

    Optional<Lead> findByPublicToken(String publicToken);

    @Query("select coalesce(max(l.id), 0) from Lead l")
    long findMaxId();

    @Query("""
            select l from Lead l left join fetch l.assignedTo
            where l.id > :after and l.id <= :last
            order by l.id
            """)
    List<Lead> findCreatedBetween(@Param("after") long after, @Param("last") long last);
}
