package com.stomatologia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public enum NotificationType {
        NEW_LEAD, CLIENT_MESSAGE
    }

    public record NotificationDto(NotificationType type, Long leadId, String title, String text, LocalDateTime at) {
    }

    /**
     * События после переданных курсоров. Клиент запоминает lastLeadId и lastMessageId и передаёт их
     * в следующем запросе; первый запрос без курсоров возвращает только курсоры.
     */
    public record NotificationFeed(long lastLeadId, long lastMessageId, List<NotificationDto> items) {
    }
}
