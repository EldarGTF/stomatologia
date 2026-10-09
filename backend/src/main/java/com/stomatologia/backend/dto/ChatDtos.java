package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Чат на сайте: посетитель видит только свой разговор и не видит имён сотрудников. */
public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * sessionId == null — первое сообщение, сервер выдаст идентификатор разговора.
     * website — скрытое поле-ловушка для ботов, как в форме записи.
     */
    public record ChatSendRequest(
            @Size(max = 64) String sessionId,
            @NotBlank(message = "Введите сообщение") @Size(max = 1000, message = "Сообщение слишком длинное") String text,
            String website) {
    }

    public record WebChatMessage(Long id, MessageRole role, String text, LocalDateTime sentAt) {

        public static WebChatMessage from(ChatMessage m) {
            return new WebChatMessage(m.getId(), m.getRole(), m.getText(), m.getSentAt());
        }
    }

    /** operator — разговор ведёт администратор: ответ придёт не сразу. */
    public record WebChatState(String sessionId, List<WebChatMessage> messages, boolean operator) {
    }
}
