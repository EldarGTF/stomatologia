package com.stomatologia.backend.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.stomatologia.backend.assistant.ConversationService;
import com.stomatologia.backend.assistant.ConversationService.Reply;
import com.stomatologia.backend.assistant.ConversationStore;
import com.stomatologia.backend.common.Phones;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.service.PublicCatalogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Telegram-бот ИИ-менеджера. Работает через long polling — публичный адрес сервера не нужен.
 * Включается, только если задан токен. Сообщения одного чата обрабатываются по очереди.
 */
@Component
public class TelegramBot implements SmartLifecycle {

    private static final Logger log = LogManager.getLogger(TelegramBot.class);
    private static final int WAIT_SECONDS = 25;
    private static final int WORKERS = 4;
    private static final long RETRY_MILLIS = 5_000;
    static final String SHARE_CONTACT = "Поделиться контактом";

    private final TelegramClient client;
    private final ConversationService conversations;
    private final ConversationStore store;
    private final PublicCatalogService catalog;

    private volatile boolean running;
    private Thread poller;
    private ExecutorService[] workers;
    private long offset;

    public TelegramBot(TelegramClient client, ConversationService conversations, ConversationStore store,
                       PublicCatalogService catalog) {
        this.client = client;
        this.conversations = conversations;
        this.store = store;
        this.catalog = catalog;
    }

    @Override
    public void start() {
        if (!client.configured()) {
            log.info("Telegram-бот выключен: не задан TELEGRAM_BOT_TOKEN");
            return;
        }
        workers = new ExecutorService[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "telegram-worker");
                t.setDaemon(true);
                return t;
            });
        }
        running = true;
        poller = new Thread(this::poll, "telegram-poller");
        poller.setDaemon(true);
        poller.start();
    }

    @Override
    public void stop() {
        running = false;
        if (poller != null) {
            poller.interrupt();
        }
        if (workers != null) {
            for (ExecutorService w : workers) {
                w.shutdownNow();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void poll() {
        try {
            log.info("Telegram-бот @{} запущен", client.botName());
        } catch (RuntimeException e) {
            log.error("Telegram-бот не запущен: {}", e.getMessage());
            running = false;
            return;
        }
        while (running) {
            try {
                for (JsonNode update : client.updates(offset, WAIT_SECONDS)) {
                    offset = Math.max(offset, update.path("update_id").asLong() + 1);
                    dispatch(update);
                }
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("Telegram: не удалось получить сообщения — {}; повтор через {} с", e.getMessage(),
                        RETRY_MILLIS / 1000);
                try {
                    Thread.sleep(RETRY_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void dispatch(JsonNode update) {
        String chatId = update.path("message").path("chat").path("id").asText("");
        if (chatId.isEmpty()) {
            return;
        }
        workers[Math.floorMod(chatId.hashCode(), WORKERS)].submit(() -> {
            try {
                handle(update);
            } catch (RuntimeException e) {
                log.error("Telegram: сбой обработки сообщения в чате {}", chatId, e);
            }
        });
    }

    /** Одно входящее обновление. Только личные чаты: в группах бот молчит. */
    void handle(JsonNode update) {
        JsonNode message = update.path("message");
        JsonNode chat = message.path("chat");
        if (!"private".equals(chat.path("type").asText())) {
            return;
        }
        String chatId = chat.path("id").asText();
        String name = fullName(message.path("from"));
        String text = message.path("text").asText("").strip();

        if (text.equals("/start") || text.startsWith("/start ")) {
            client.sendMessage(chatId, greeting(), contactKeyboard());
            return;
        }
        JsonNode contact = message.path("contact");
        if (!contact.isMissingNode()) {
            if (contact.path("user_id").asLong(-1) != message.path("from").path("id").asLong(-2)) {
                client.sendMessage(chatId, "Пожалуйста, отправьте свой контакт кнопкой «" + SHARE_CONTACT
                        + "» или напишите номер сообщением.", null);
                return;
            }
            String phone = Phones.normalize(contact.path("phone_number").asText());
            store.rememberPhone(ChatChannel.TELEGRAM, chatId, phone, fullName(contact));
            answer(chatId, "Мой номер телефона: " + phone, name, Map.of("remove_keyboard", true));
            return;
        }
        if (text.isEmpty()) {
            client.sendMessage(chatId, "Пока я понимаю только текст. Напишите, пожалуйста, вопрос сообщением.", null);
            return;
        }
        answer(chatId, text, name, null);
    }

    private void answer(String chatId, String text, String name, Object keyboard) {
        try {
            client.typing(chatId);
        } catch (RuntimeException e) {
            log.debug("Telegram: не удалось показать «печатает»: {}", e.getMessage());
        }
        Reply reply = conversations.handle(ChatChannel.TELEGRAM, chatId, text, name);
        List<String> texts = reply.texts();
        for (int i = 0; i < texts.size(); i++) {
            client.sendMessage(chatId, texts.get(i), i == 0 ? keyboard : null);
        }
    }

    private String greeting() {
        return "Здравствуйте! Я ИИ-помощник клиники «" + catalog.clinic().name() + "». Расскажу об услугах и ценах "
                + "и запишу на приём. Чтобы записаться быстрее, можно поделиться номером телефона кнопкой ниже. "
                + "Чем могу помочь?";
    }

    static Map<String, Object> contactKeyboard() {
        return Map.of("keyboard", List.of(List.of(Map.of("text", SHARE_CONTACT, "request_contact", true))),
                "resize_keyboard", true, "one_time_keyboard", true);
    }

    private static String fullName(JsonNode person) {
        String first = person.path("first_name").asText("").strip();
        String last = person.path("last_name").asText("").strip();
        String full = (first + " " + last).strip();
        return full.isEmpty() ? null : full;
    }
}
