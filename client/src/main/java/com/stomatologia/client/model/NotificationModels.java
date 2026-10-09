package com.stomatologia.client.model;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationModels {

    private NotificationModels() {
    }

    public enum NotificationType {
        NEW_LEAD, CLIENT_MESSAGE
    }

    public record NotificationDto(NotificationType type, Long leadId, String title, String text, LocalDateTime at) {
    }

    public record NotificationFeed(long lastLeadId, long lastMessageId, List<NotificationDto> items) {
    }
}
