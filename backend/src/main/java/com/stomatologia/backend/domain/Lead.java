package com.stomatologia.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Заявка (лид): обращение до записи на приём. Приходит из мессенджера, с сайта или по телефону,
 * регистратор превращает её в пациента и запись.
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status = LeadStatus.NEW;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ClinicService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    /** Время, выбранное клиентом из свободных окон. */
    private LocalDateTime preferredStart;

    /** Пожелание по времени своими словами, например «в субботу утром». */
    @Column(length = 200)
    private String preferredText;

    /** Суть обращения: жалобы и вопросы, кратко. */
    @Column(columnDefinition = "text")
    private String summary;

    /** Когда клиент согласился на обработку персональных данных. */
    private LocalDateTime consentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(length = 300)
    private String rejectReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
