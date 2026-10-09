package com.stomatologia.backend.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bot API Telegram. Токен входит в адрес запроса, поэтому адрес нигде не логируется.
 */
@Component
public class TelegramClient {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper json;
    private final String token;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TelegramClient(ObjectMapper json, @Value("${app.telegram.bot-token:}") String token,
                          @Value("${app.telegram.base-url:https://api.telegram.org}") String baseUrl) {
        this.json = json;
        this.token = token == null ? "" : token.trim();
        this.baseUrl = baseUrl;
    }

    public boolean configured() {
        return !token.isEmpty();
    }

    public static class TelegramException extends RuntimeException {
        public TelegramException(String message) {
            super(message);
        }
    }

    /** Имя бота (@username) — заодно проверка, что токен верный. */
    public String botName() {
        return call("getMe", Map.of(), SEND_TIMEOUT).path("username").asText();
    }

    /** Long polling: ждёт новых сообщений до waitSeconds. */
    public List<JsonNode> updates(long offset, int waitSeconds) {
        JsonNode result = call("getUpdates", Map.of("offset", offset, "timeout", waitSeconds,
                "allowed_updates", List.of("message")), Duration.ofSeconds(waitSeconds + 10L));
        List<JsonNode> list = new ArrayList<>();
        result.forEach(list::add);
        return list;
    }

    /** replyMarkup — клавиатура или null. */
    public void sendMessage(String chatId, String text, Object replyMarkup) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("link_preview_options", Map.of("is_disabled", true));
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        call("sendMessage", body, SEND_TIMEOUT);
    }

    /** «печатает…» в чате, пока ИИ-менеджер готовит ответ. */
    public void typing(String chatId) {
        call("sendChatAction", Map.of("chat_id", chatId, "action", "typing"), SEND_TIMEOUT);
    }

    private JsonNode call(String method, Map<String, Object> body, Duration timeout) {
        if (!configured()) {
            throw new TelegramException("Telegram-бот не настроен: не задан TELEGRAM_BOT_TOKEN");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/bot" + token + "/" + method))
                    .timeout(timeout)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = json.readTree(response.body());
            if (!root.path("ok").asBoolean(false)) {
                throw new TelegramException("Telegram " + method + ": "
                        + root.path("description").asText("HTTP " + response.statusCode()));
            }
            return root.path("result");
        } catch (IOException e) {
            throw new TelegramException("Telegram " + method + ": нет связи (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException("Telegram " + method + ": прервано");
        }
    }
}
