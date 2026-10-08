package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
