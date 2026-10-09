package com.stomatologia.backend.assistant;

import com.stomatologia.backend.assistant.ChatModel.AssistantTurn;
import com.stomatologia.backend.assistant.ChatModel.ModelMessage;
import com.stomatologia.backend.assistant.ChatModel.ModelReply;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolResults;
import com.stomatologia.backend.assistant.ChatModel.UserText;
import com.stomatologia.backend.assistant.ConversationStore.ChatState;
import com.stomatologia.backend.assistant.ConversationStore.OperatorTarget;
import com.stomatologia.backend.assistant.ConversationStore.Received;
import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.dto.PublicDtos.ClinicInfo;
import com.stomatologia.backend.security.AuthUser;
import com.stomatologia.backend.security.CurrentUser;
import com.stomatologia.backend.service.PublicCatalogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Разговор с клиентом в любом канале: сохраняет сообщение, спрашивает модель, выполняет её инструменты
 * и сохраняет ответ. Если модель недоступна или разговор ведёт оператор — модель не вызывается.
 * Транзакции короткие (в {@link ConversationStore}), ответ модели ждём вне транзакции.
 */
@Service
public class ConversationService {

    private static final Logger log = LogManager.getLogger(ConversationService.class);
    static final int MAX_TOOL_STEPS = 6;
    static final int MAX_TEXT = 2000;
    private static final int LOCK_STRIPES = 64;
    private static final Pattern BOOKING_CLAIM = Pattern.compile(
            "записан[аы]?\\b|записал[аи]?\\b|перезаписал|запись\\s+(оформлен|создан|подтвержден|готова)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    static final String NOT_BOOKED = "[Служебное сообщение системы, клиент его не видит] Запись НЕ создана: "
            + "book_appointment не вызывался или вернул ошибку. Если клиент подтвердил время и согласие — вызови "
            + "find_free_slots и book_appointment сейчас. Иначе ответь клиенту заново, не утверждая, что он записан.";
    private static final Pattern NO_SLOTS_CLAIM = Pattern.compile(
            "(нет|не\\s+осталось|закончил|занят|закрыт)[^.!?]{0,40}(мест|врем|слот|окошк|окон)"
                    + "|(мест|врем|слот|окошк|окон)[^.!?]{0,40}(нет|не\\s+осталось|закончил|занят|закрыт)"
                    + "|запис[^.!?]{0,40}закрыт|закрыт[^.!?]{0,40}запис",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    static final String NOT_SEARCHED = "[Служебное сообщение системы, клиент его не видит] Ты сказал, что "
            + "свободного времени нет, но в этом ответе не вызвал find_free_slots — это догадка. Вызови find_free_slots "
            + "(с датой, если клиент её назвал) и ответь по его результату.";

    private final ChatModel model;
    private final AssistantTools tools;
    private final ConversationStore store;
    private final PublicCatalogService catalog;
    private final Map<ChatChannel, ChatGateway> gateways = new EnumMap<>(ChatChannel.class);
    private final int historyMessages;
    private final int dailyMessagesPerChat;
    private final Object[] locks = new Object[LOCK_STRIPES];

    public ConversationService(ChatModel model, AssistantTools tools, ConversationStore store,
                               PublicCatalogService catalog, List<ChatGateway> gateways,
                               @Value("${app.assistant.history-messages:30}") int historyMessages,
                               @Value("${app.assistant.daily-messages-per-chat:60}") int dailyMessagesPerChat) {
        this.model = model;
        this.tools = tools;
        this.store = store;
        this.catalog = catalog;
        gateways.forEach(g -> this.gateways.put(g.channel(), g));
        this.historyMessages = historyMessages;
        this.dailyMessagesPerChat = dailyMessagesPerChat;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    /** Итог входящего сообщения: ответы клиенту (могут быть пустыми, если разговор ведёт оператор). */
    public record Reply(Long conversationId, Long userMessageId, List<String> texts, boolean operator) {
    }

    /**
     * Входящее сообщение клиента. Сообщения одного чата обрабатываются по очереди, чтобы модель видела
     * их в правильном порядке.
     */
    public Reply handle(ChatChannel channel, String chatId, String text, String clientName) {
        String clean = text.strip();
        if (clean.length() > MAX_TEXT) {
            clean = clean.substring(0, MAX_TEXT);
        }
        synchronized (lockFor(channel, chatId)) {
            Received in = store.receive(channel, chatId, clean, clientName);
            if (in.mode() == ConversationMode.OPERATOR) {
                return new Reply(in.conversationId(), in.messageId(), List.of(), true);
            }
            if (!model.available()) {
                return handOff(in, "ИИ-менеджер выключен (не задан ключ API)");
            }
            if (in.userMessagesToday() > dailyMessagesPerChat) {
                return handOff(in, "клиент написал больше " + dailyMessagesPerChat + " сообщений за день");
            }
            String answer;
            try {
                answer = converse(in.conversationId());
            } catch (ChatModelException e) {
                log.warn("Разговор #{}: модель не ответила — {}", in.conversationId(), e.getMessage());
                return handOff(in, "ИИ-менеджер временно недоступен");
            }
            if (answer == null) {
                return handOff(in, "ИИ-менеджер не смог сформулировать ответ");
            }
            store.saveReply(in.conversationId(), MessageRole.ASSISTANT, answer, null);
            boolean operator = store.state(in.conversationId()).mode() == ConversationMode.OPERATOR;
            return new Reply(in.conversationId(), in.messageId(), List.of(answer), operator);
        }
    }

    /** Ход модели с инструментами; null — модель так и не дала текстовый ответ. */
    private String converse(Long conversationId) {
        ChatState chat = store.state(conversationId);
        ClinicInfo clinic = catalog.clinic();
        String system = AssistantPrompt.build(clinic, chat, LocalDateTime.now());
        List<ModelMessage> messages = history(conversationId);
        boolean booked = chat.appointmentStart() != null;
        boolean searched = false;
        boolean corrected = false;
        for (int step = 0; step < MAX_TOOL_STEPS; step++) {
            ModelReply reply = model.reply(system, messages, tools.specs());
            if (!reply.wantsTools()) {
                String text = clean(reply.text());
                boolean falseBooking = !booked && claimsBooking(text);
                boolean guessedSlots = !searched && claimsNoSlots(text);
                if (!falseBooking && !guessedSlots) {
                    return text;
                }
                log.warn("Разговор #{}: модель {} без инструмента: {}", conversationId,
                        falseBooking ? "сообщила о записи" : "сказала, что времени нет,", text);
                if (corrected) {
                    return falseBooking ? null : text;
                }
                corrected = true;
                messages.add(AssistantTurn.text(text));
                messages.add(new UserText(falseBooking ? NOT_BOOKED : NOT_SEARCHED));
                continue;
            }
            AssistantTurn turn = new AssistantTurn(reply.text(), reply.toolCalls());
            messages.add(turn);
            List<ToolResult> results = new ArrayList<>();
            for (ToolCall call : reply.toolCalls()) {
                log.info("Разговор #{}: инструмент {} {}", conversationId, call.name(), call.input());
                ToolResult result = tools.execute(call, conversationId);
                booked |= call.name().equals("book_appointment") && !result.error();
                searched |= call.name().equals("find_free_slots") && !result.error();
                results.add(result);
            }
            messages.add(new ToolResults(results));
            store.saveReply(conversationId, MessageRole.TOOL, ToolTurns.write(turn, results), null);
        }
        log.warn("Разговор #{}: модель вызвала инструменты {} раз подряд без ответа", conversationId, MAX_TOOL_STEPS);
        return null;
    }

    /**
     * Переписка текущей заявки так, как её видела модель, вместе с ходами инструментов. Начинается
     * с сообщения клиента: обрезанный по лимиту ход с инструментом API не примет.
     */
    private List<ModelMessage> history(Long conversationId) {
        List<ModelMessage> messages = new ArrayList<>();
        for (ChatMessage m : store.history(conversationId, historyMessages)) {
            if (messages.isEmpty() && m.getRole() != MessageRole.USER) {
                continue;
            }
            switch (m.getRole()) {
                case USER -> messages.add(new UserText(m.getText()));
                case ASSISTANT -> messages.add(AssistantTurn.text(m.getText()));
                case OPERATOR -> messages.add(AssistantTurn.text("Администратор: " + m.getText()));
                case TOOL -> messages.addAll(ToolTurns.read(m.getText()));
            }
        }
        return messages;
    }

    private Reply handOff(Received in, String reason) {
        store.handoff(in.conversationId(), reason);
        String text = fallbackText();
        store.saveReply(in.conversationId(), MessageRole.ASSISTANT, text, null);
        return new Reply(in.conversationId(), in.messageId(), List.of(text), true);
    }

    String fallbackText() {
        String phone = catalog.clinic().phone();
        return "Спасибо, что написали! Ваш вопрос передан администратору — он ответит здесь же в рабочее время."
                + (phone == null || phone.isBlank() ? "" : " Если срочно, позвоните: " + phone + ".");
    }

    /** Ответ оператора из карточки заявки. Сначала сохраняется, затем доставляется в мессенджер. */
    public void operatorReply(Long leadId, String text) {
        AuthUser me = CurrentUser.get();
        OperatorTarget target = store.operatorReply(leadId, text.strip(), me.id(), me.username());
        ChatGateway gateway = gateways.get(target.channel());
        if (gateway == null) {
            return;
        }
        try {
            gateway.send(target.chatId(), text.strip());
        } catch (RuntimeException e) {
            log.error("Заявка #{}: сообщение оператора не доставлено в {}", leadId, target.channel().title(), e);
            throw ApiException.conflict("Сообщение сохранено, но не доставлено в " + target.channel().title()
                    + ": " + e.getMessage());
        }
    }

    public void returnToAssistant(Long leadId) {
        store.returnToAssistant(leadId, CurrentUser.get().username());
    }

    private Object lockFor(ChatChannel channel, String chatId) {
        return locks[Math.floorMod((channel.name() + ":" + chatId).hashCode(), LOCK_STRIPES)];
    }

    static boolean claimsBooking(String text) {
        return text != null && BOOKING_CLAIM.matcher(text).find();
    }

    static boolean claimsNoSlots(String text) {
        return text != null && NO_SLOTS_CLAIM.matcher(text).find();
    }

    /** Модель иногда всё же присылает Markdown — в мессенджере он выглядит как мусор. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String s = text.replace("**", "").replace("__", "").replaceAll("(?m)^#{1,6}\\s+", "").strip();
        return s.isEmpty() ? null : s;
    }
}
