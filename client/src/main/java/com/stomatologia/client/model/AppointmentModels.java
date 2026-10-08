package com.stomatologia.client.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AppointmentModels {

    private AppointmentModels() {
    }

    public enum AppointmentStatus {
        SCHEDULED("Запланирован", "badge-info"),
        COMPLETED("Завершён", "badge-success"),
        CANCELLED("Отменён", "badge-danger"),
        NO_SHOW("Неявка", "badge-warning");

        private final String title;
        private final String styleClass;

        AppointmentStatus(String title, String styleClass) {
            this.title = title;
            this.styleClass = styleClass;
        }

        public String title() {
            return title;
        }

        public String styleClass() {
            return styleClass;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public record AppointmentDto(Long id, Long patientId, String patientName, String patientPhone,
                                 Long doctorId, String doctorName, String specialtyName,
                                 Long roomId, String roomNumber, Long serviceId, String serviceName,
                                 BigDecimal price, LocalDateTime startAt, LocalDateTime endAt,
                                 AppointmentStatus status, String notes, LocalDateTime createdAt) {

        public boolean scheduled() {
            return status == AppointmentStatus.SCHEDULED;
        }

        public boolean started() {
            return !startAt.isAfter(LocalDateTime.now());
        }
    }

    public record AppointmentRequest(Long patientId, Long doctorId, Long serviceId, LocalDateTime startAt,
                                     String notes) {
    }

    public record CancelRequest(String reason) {
    }

    public record StatusRequest(AppointmentStatus status) {
    }

    public record AuditDto(Long id, String action, String oldValue, String newValue, String changedBy,
                           LocalDateTime changedAt) {

        public String actionTitle() {
            return switch (action) {
                case "CREATE" -> "Создание";
                case "UPDATE" -> "Изменение";
                case "RESCHEDULE" -> "Перенос";
                case "CANCEL" -> "Отмена";
                case "STATUS" -> "Смена статуса";
                default -> action;
            };
        }
    }

    public record SlotDto(Long doctorId, String doctorName, String roomNumber, LocalDateTime start,
                          LocalDateTime end) {
    }
}
