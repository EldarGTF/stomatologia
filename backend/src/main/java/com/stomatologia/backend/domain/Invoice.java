package com.stomatologia.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Счёт за приём. Один приём — не более одного счёта; оплата может вноситься частями.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paidAt")
    private List<Payment> payments = new ArrayList<>();

    public BigDecimal paidAmount() {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal dueAmount() {
        return status == InvoiceStatus.CANCELLED ? BigDecimal.ZERO : amount.subtract(paidAmount()).max(BigDecimal.ZERO);
    }

    /** Пересчитывает статус по сумме внесённых платежей (аннулированный счёт не трогает). */
    public void refreshStatus() {
        if (status == InvoiceStatus.CANCELLED) {
            return;
        }
        BigDecimal paid = paidAmount();
        if (paid.signum() == 0) {
            status = amount.signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.UNPAID;
        } else {
            status = paid.compareTo(amount) >= 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL;
        }
    }
}
