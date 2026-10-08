package com.stomatologia.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Настройки клиники — единственная запись (id = 1): реквизиты для документов, правила записи и счетов.
 */
@Entity
@Table(name = "clinic_settings")
@Getter
@Setter
@NoArgsConstructor
public class ClinicSettings {

    public static final short ID = 1;

    @Id
    private Short id = ID;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 12)
    private String bin;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(length = 34)
    private String iik;

    @Column(length = 11)
    private String bik;

    @Column(name = "slot_step_minutes", nullable = false)
    private int slotStepMinutes = 15;

    @Column(name = "booking_horizon_days", nullable = false)
    private int bookingHorizonDays = 60;

    @Column(name = "min_lead_hours", nullable = false)
    private int minLeadHours = 1;

    @Column(name = "patient_cancel_hours", nullable = false)
    private int patientCancelHours = 24;

    @Column(name = "invoice_prefix", nullable = false, length = 10)
    private String invoicePrefix = "СЧ";

    @Column(name = "vat_enabled", nullable = false)
    private boolean vatEnabled;

    @Column(name = "vat_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal vatRate = new BigDecimal("12");

    /** Услуги от этой цены требуют предоплаты; null — предоплата не требуется. */
    @Column(name = "prepayment_threshold", precision = 10, scale = 2)
    private BigDecimal prepaymentThreshold;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** Последний день, на который открыта запись. */
    public LocalDate lastBookableDay(LocalDate today) {
        return today.plusDays(bookingHorizonDays);
    }

    public boolean requiresPrepayment(BigDecimal price) {
        return prepaymentThreshold != null && price != null && price.compareTo(prepaymentThreshold) >= 0;
    }
}
