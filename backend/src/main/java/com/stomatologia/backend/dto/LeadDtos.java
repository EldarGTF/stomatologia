package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class LeadDtos {

    private LeadDtos() {
    }

    public record LeadDto(Long id, LeadSource source, LeadStatus status, String name, String phone,
                          Long serviceId, String serviceName, Long doctorId, String doctorName,
                          LocalDateTime preferredStart, String preferredText, String summary, LocalDateTime consentAt,
                          Long patientId, String patientName, Long appointmentId, LocalDateTime appointmentStart,
                          String assignedTo, String rejectReason, LocalDateTime createdAt, LocalDateTime updatedAt,
                          boolean hasConversation, LocalDateTime confirmedAt, boolean awaitsConfirmation) {

        public static LeadDto from(Lead l, boolean hasConversation) {
            return new LeadDto(l.getId(), l.getSource(), l.getStatus(), l.getName(), l.getPhone(),
                    l.getService() != null ? l.getService().getId() : null,
                    l.getService() != null ? l.getService().getName() : null,
                    l.getDoctor() != null ? l.getDoctor().getId() : null,
                    l.getDoctor() != null ? l.getDoctor().getFullName() : null,
                    l.getPreferredStart(), l.getPreferredText(), l.getSummary(), l.getConsentAt(),
                    l.getPatient() != null ? l.getPatient().getId() : null,
                    l.getPatient() != null ? l.getPatient().getFullName() : null,
                    l.getAppointment() != null ? l.getAppointment().getId() : null,
                    l.getAppointment() != null ? l.getAppointment().getStartAt() : null,
                    l.getAssignedTo() != null ? l.getAssignedTo().getFullName() : null,
                    l.getRejectReason(), l.getCreatedAt(), l.getUpdatedAt(), hasConversation,
                    l.getConfirmedAt(), l.awaitsConfirmation());
        }
    }

    public record LeadRequest(
            @NotNull(message = "Укажите источник заявки") LeadSource source,
            @NotBlank(message = "Укажите имя клиента") @Size(max = 100, message = "Имя слишком длинное") String name,
            @Pattern(regexp = "^$|^[+0-9 ()-]{5,30}$", message = "Телефон может содержать только цифры, пробелы, +, -, ()")
            String phone,
            Long serviceId,
            Long doctorId,
            LocalDateTime preferredStart,
            @Size(max = 200, message = "Пожелание по времени слишком длинное") String preferredText,
            @Size(max = 2000, message = "Описание слишком длинное") String summary) {
    }

    /**
     * Запись по заявке: существующий пациент (patientId) или новый — по фамилии, имени и телефону.
     */
    public record LeadBookRequest(
            Long patientId,
            @Size(max = 60, message = "Фамилия слишком длинная") String lastName,
            @Size(max = 60, message = "Имя слишком длинное") String firstName,
            @Pattern(regexp = "^$|^[+0-9 ()-]{5,30}$", message = "Телефон может содержать только цифры, пробелы, +, -, ()")
            String phone,
            @NotNull(message = "Выберите врача") Long doctorId,
            @NotNull(message = "Выберите услугу") Long serviceId,
            @NotNull(message = "Выберите дату и время") LocalDateTime startAt,
            @Size(max = 500, message = "Комментарий слишком длинный") String notes) {
    }

    public record RejectRequest(
            @NotBlank(message = "Укажите причину отказа") @Size(max = 300, message = "Причина слишком длинная")
            String reason) {
    }

    public record ChatMessageDto(Long id, MessageRole role, String text, String author, LocalDateTime sentAt) {

        public static ChatMessageDto from(ChatMessage m) {
            return new ChatMessageDto(m.getId(), m.getRole(), m.getText(),
                    m.getAuthor() != null ? m.getAuthor().getFullName() : null, m.getSentAt());
        }
    }

    /** Показатели заявок для Dashboard. */
    public record LeadStats(int newToday, int open, int conversionPercent) {
    }
}
