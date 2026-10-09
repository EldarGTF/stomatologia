package com.stomatologia.backend.web;

import com.stomatologia.backend.assistant.ConversationService;
import com.stomatologia.backend.assistant.ConversationService.Reply;
import com.stomatologia.backend.assistant.ConversationStore;
import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.dto.ChatDtos.ChatSendRequest;
import com.stomatologia.backend.dto.ChatDtos.WebChatMessage;
import com.stomatologia.backend.dto.ChatDtos.WebChatState;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Чат на сайте. Разговор определяется случайным sessionId, который виджет хранит в браузере.
 * Частота сообщений ограничена {@link PublicRateLimitFilter}.
 */
@RestController
@RequestMapping("/api/public/chat")
public class PublicChatController {

    private static final Logger log = LogManager.getLogger(PublicChatController.class);
    private static final Pattern SESSION = Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConversationService conversations;
    private final ConversationStore store;

    public PublicChatController(ConversationService conversations, ConversationStore store) {
        this.conversations = conversations;
        this.store = store;
    }

    @PostMapping("/messages")
    public WebChatState send(@Valid @RequestBody ChatSendRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            log.warn("Чат на сайте: заполнено скрытое поле, похоже на бота");
            throw ApiException.badRequest("Не удалось отправить сообщение. Обновите страницу");
        }
        String session = request.sessionId() != null && SESSION.matcher(request.sessionId()).matches()
                ? request.sessionId() : newSession();
        Reply reply = conversations.handle(ChatChannel.WEB_CHAT, session, request.text(), null);
        return new WebChatState(session, store.messagesAfter(ChatChannel.WEB_CHAT, session, reply.userMessageId() - 1)
                .stream().map(WebChatMessage::from).toList(), reply.operator());
    }

    @GetMapping("/{sessionId}")
    public WebChatState poll(@PathVariable String sessionId, @RequestParam(defaultValue = "0") long after) {
        if (!SESSION.matcher(sessionId).matches()) {
            throw ApiException.notFound("Разговор не найден");
        }
        return new WebChatState(sessionId, store.messagesAfter(ChatChannel.WEB_CHAT, sessionId, after)
                .stream().map(WebChatMessage::from).toList(),
                store.mode(ChatChannel.WEB_CHAT, sessionId) == ConversationMode.OPERATOR);
    }

    private static String newSession() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
