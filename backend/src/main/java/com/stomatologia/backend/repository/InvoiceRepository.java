package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.InvoiceStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByAppointmentId(Long appointmentId);

    @EntityGraph(attributePaths = "payments")
    List<Invoice> findByStatusIn(Collection<InvoiceStatus> statuses);

    @Override
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.doctor", "appointment.service",
            "payments", "payments.receivedBy"})
    List<Invoice> findAll(Specification<Invoice> spec, Sort sort);
}
