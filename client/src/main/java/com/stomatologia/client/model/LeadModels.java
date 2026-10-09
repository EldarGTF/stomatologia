package com.stomatologia.client.model;

import java.time.LocalDateTime;

public final class LeadModels {

    private LeadModels() {
    }

    public enum LeadSource {
        TELEGRAM("Telegram", "badge-info"),
        WHATSAPP("WhatsApp", "badge-success"),
        WEBSITE("Сайт", "badge-info"),
        PHONE("Телефон", "badge-muted");

        private final String title;
        private final String styleClass;

        LeadSource(String title, String styleClass) {
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

    public enum LeadStatus {
        NEW("Новая", "badge-warning"),
        IN_PROGRESS("В работе", "badge-info"),
        NEEDS_OPERATOR("Нужен оператор", "badge-danger"),
        BOOKED("Записан", "badge-success"),
        REJECTED("Отказ", "badge-muted");

        private final String title;
        private final String styleClass;

        LeadStatus(String title, String styleClass) {
            this.title = title;
            this.styleClass = styleClass;
        }

        public String title() {
            return title;
        }

        public String styleClass() {
            return styleClass;
        }

        public boolean open() {
            return this == NEW || this == IN_PROGRESS || this == NEEDS_OPERATOR;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum MessageRole {
        USER,
        ASSISTANT,
        OPERATOR
    }

    public enum ChatChannel {
        TELEGRAM("Telegram"),
        WHATSAPP("WhatsApp"),
        WEB_CHAT("Чат на сайте");

        private final String title;

        ChatChannel(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Кто сейчас отвечает клиенту в переписке. */
    public enum ConversationMode {
        AI("Отвечает ИИ-менеджер", "badge-info"),
        OPERATOR("Отвечает оператор", "badge-warning"),
        CLOSED("Разговор закрыт", "badge-muted");

        private final String title;
        private final String styleClass;

        ConversationMode(String title, String styleClass) {
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

    public record LeadDto(Long id, LeadSource source, LeadStatus status, String name, String phone,
                          Long serviceId, String serviceName, Long doctorId, String doctorName,
                          LocalDateTime preferredStart, String preferredText, String summary, LocalDateTime consentAt,
                          Long patientId, String patientName, Long appointmentId, LocalDateTime appointmentStart,
                          String assignedTo, String rejectReason, LocalDateTime createdAt, LocalDateTime updatedAt,
                          boolean hasConversation, LocalDateTime confirmedAt, boolean awaitsConfirmation,
                          ChatChannel conversationChannel, ConversationMode conversationMode) {

        /** Онлайн-запись с сайта, которую администратор ещё не подтвердил звонком, тоже ждёт обработки. */
        public boolean needsAttention() {
            return status.open() || awaitsConfirmation;
        }

        public String statusTitle() {
            return awaitsConfirmation ? "Не подтверждена" : status.title();
        }

        public String statusStyle() {
            return awaitsConfirmation ? "badge-warning" : status.styleClass();
        }
    }

    public record LeadRequest(LeadSource source, String name, String phone, Long serviceId, Long doctorId,
                              LocalDateTime preferredStart, String preferredText, String summary) {
    }

    public record LeadBookRequest(Long patientId, String lastName, String firstName, String phone, Long doctorId,
                                  Long serviceId, LocalDateTime startAt, String notes) {
    }

    public record RejectRequest(String reason) {
    }

    public record OperatorMessageRequest(String text) {
    }

    public record ChatMessageDto(Long id, MessageRole role, String text, String author, LocalDateTime sentAt) {
    }
}
