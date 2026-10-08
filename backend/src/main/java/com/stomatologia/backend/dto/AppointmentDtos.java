package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentAudit;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.AuditAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record AppointmentDto(Long id, Long patientId, String patientName, String patientPhone,
                                 Long doctorId, String doctorName, String specialtyName,
                                 Long roomId, String roomNumber, Long serviceId, String serviceName,
                                 BigDecimal price, LocalDateTime startAt, LocalDateTime endAt,
                                 AppointmentStatus status, String notes, LocalDateTime createdAt) {

        public static AppointmentDto from(Appointment a) {
            return new AppointmentDto(a.getId(),
                    a.getPatient().getId(), a.getPatient().getFullName(), a.getPatient().getPhone(),
                    a.getDoctor().getId(), a.getDoctor().getFullName(), a.getDoctor().getSpecialty().getName(),
                    a.getRoom().getId(), a.getRoom().getNumber(),
                    a.getService().getId(), a.getService().getName(), a.getService().getPrice(),
                    a.getStartAt(), a.getEndAt(), a.getStatus(), a.getNotes(), a.getCreatedAt());
        }
    }

    public record AppointmentRequest(
            Long patientId,
            @NotNull(message = "Выберите врача") Long doctorId,
            @NotNull(message = "Выберите услугу") Long serviceId,
            @NotNull(message = "Выберите дату и время") LocalDateTime startAt,
            @Size(max = 500, message = "Комментарий слишком длинный") String notes) {
    }

    public record CancelRequest(@Size(max = 300, message = "Причина слишком длинная") String reason) {
    }

    public record StatusRequest(@NotNull(message = "Укажите статус") AppointmentStatus status) {
    }

    public record AuditDto(Long id, AuditAction action, String oldValue, String newValue, String changedBy,
                           LocalDateTime changedAt) {

        public static AuditDto from(AppointmentAudit a) {
            return new AuditDto(a.getId(), a.getAction(), a.getOldValue(), a.getNewValue(),
                    a.getChangedBy() != null ? a.getChangedBy().getFullName() : "система", a.getChangedAt());
        }
    }

    public record SlotDto(Long doctorId, String doctorName, String roomNumber, LocalDateTime start,
                          LocalDateTime end) {
    }
}
