package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.AppointmentAudit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentAuditRepository extends JpaRepository<AppointmentAudit, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<AppointmentAudit> findByAppointmentIdOrderByChangedAtDescIdDesc(Long appointmentId);
}
