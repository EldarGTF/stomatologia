package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.paidAt >= :from and p.paidAt < :to")
    BigDecimal sumBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Payment> findByPaidAtGreaterThanEqualAndPaidAtLessThan(LocalDateTime from, LocalDateTime to);
}
