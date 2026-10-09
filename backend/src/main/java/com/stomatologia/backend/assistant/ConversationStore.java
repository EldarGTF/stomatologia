package com.stomatologia.backend.assistant;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.common.Phones;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.ConversationRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Разговоры и сообщения в базе. Каждый метод — короткая транзакция: ответ модели ждём вне транзакции.
 */
@Service
public class ConversationStore {

    private static final Logger log = LogManager.getLogger(ConversationStore.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final LeadRepository leads;
    private final ClinicServiceRepository services;
    private final UserRepository users;

    public ConversationStore(ConversationRepository conversations, ChatMessageRepository messages,
                             LeadRepository leads, ClinicServiceRepository services, UserRepository users) {
        this.conversations = conversations;
        this.messages = messages;
        this.leads = leads;
        this.services = services;
        this.users = users;
    }

    /** Входящее сообщение сохранено; userMessagesToday — сколько сообщений клиент написал сегодня. */
    public record Received(Long conversationId, Long messageId, ConversationMode mode, long userMessagesToday) {
    }

    /** Снимок разговора для промпта и инструментов. */
    public record ChatState(Long id, ChatChannel channel, ConversationMode mode, String clientName,
                            String clientPhone, Long leadId, LeadStatus leadStatus, LocalDateTime appointmentStart) {
    }

    @Transactional
    public Received receive(ChatChannel channel, String chatId, String text, String clientName) {
        Conversation c = openConversation(channel, chatId);
        if (c.getClientName() == null && clientName != null && !clientName.isBlank()) {
            c.setClientName(cut(clientName.trim(), 100));
        }
        if (c.getLead() == null) {
            Lead l = openLead(c);
            l.setSummary("Написал в " + c.getChannel().title() + ": " + cut(text, 300));
            leads.saveAndFlush(l);
            c.setLead(l);
            log.info("Разговор #{} ({}): новая заявка #{}", c.getId(), c.getChannel().title(), l.getId());
        }
        ChatMessage m = save(c, MessageRole.USER, text, null);
        long today = messages.countByConversationIdAndRoleAndSentAtGreaterThanEqual(c.getId(), MessageRole.USER,
                LocalDate.now().atStartOfDay());
        return new Received(c.getId(), m.getId(), c.getMode(), today);
    }

    /** Номер, подтверждённый мессенджером: кнопка «Поделиться контактом». */
    @Transactional
    public void rememberPhone(ChatChannel channel, String chatId, String phone, String name) {
        Conversation c = openConversation(channel, chatId);
        c.setClientPhone(Phones.normalize(phone));
        if (name != null && !name.isBlank()) {
            c.setClientName(cut(name.trim(), 100));
        }
        if (c.getLead() != null && c.getLead().getStatus().isOpen() && c.getLead().getPhone() == null) {
            c.getLead().setPhone(c.getClientPhone());
        }
    }

    @Transactional
    public Long saveReply(Long conversationId, MessageRole role, String text, Long authorId) {
        return save(find(conversationId), role, text, authorId).getId();
    }

    /** Последние limit сообщений в хронологическом порядке. */
    @Transactional(readOnly = true)
    public List<ChatMessage> history(Long conversationId, int limit) {
        List<ChatMessage> list = new ArrayList<>(
                messages.findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, limit)));
        Collections.reverse(list);
        return list;
    }

    @Transactional(readOnly = true)
    public ChatState state(Long conversationId) {
        Conversation c = find(conversationId);
        Lead l = c.getLead();
        return new ChatState(c.getId(), c.getChannel(), c.getMode(), c.getClientName(), c.getClientPhone(),
                l != null ? l.getId() : null, l != null ? l.getStatus() : null,
                l != null && l.getAppointment() != null ? l.getAppointment().getStartAt() : null);
    }

    @Transactional
    public void linkLead(Long conversationId, Long leadId) {
        find(conversationId).setLead(leads.getReferenceById(leadId));
    }

    /**
     * ИИ-менеджер передаёт разговор человеку: режим «оператор», заявка — «Нужен оператор».
     * Если открытой заявки нет (не было или уже записан), заводится новая — чтобы её увидел регистратор.
     */
    @Transactional
    public Long handoff(Long conversationId, String reason) {
        Conversation c = find(conversationId);
        c.setMode(ConversationMode.OPERATOR);
        Lead l = openLead(c);
        l.setStatus(LeadStatus.NEEDS_OPERATOR);
        l.setSummary(appendLine(l.getSummary(), "Нужен оператор: " + reason));
        leads.saveAndFlush(l);
        c.setLead(l);
        log.info("Разговор #{} ({}) передан оператору, заявка #{}: {}", c.getId(), c.getChannel().title(),
                l.getId(), reason);
        return l.getId();
    }

    /** Заявка на обратный звонок: подходящего времени нет или клиент хочет, чтобы ему перезвонили. */
    @Transactional
    public Long callbackLead(Long conversationId, String name, String phone, Long serviceId, String preferredText,
                             String summary) {
        Conversation c = find(conversationId);
        Lead l = openLead(c);
        if (name != null && !name.isBlank()) {
            l.setName(cut(name.trim(), 100));
        }
        if (phone != null && !phone.isBlank()) {
            l.setPhone(Phones.normalize(phone));
        }
        if (serviceId != null) {
            l.setService(services.findById(serviceId).filter(s -> s.isActive())
                    .orElseThrow(() -> ApiException.notFound("Услуга не найдена")));
        }
        if (preferredText != null && !preferredText.isBlank()) {
            l.setPreferredText(cut(preferredText.trim(), 200));
        }
        if (summary != null && !summary.isBlank()) {
            l.setSummary(cut(summary.trim(), 2000));
        }
        l.setConsentAt(LocalDateTime.now());
        leads.saveAndFlush(l);
        c.setLead(l);
        log.info("Разговор #{} ({}): заявка #{} на обратный звонок — {} {}", c.getId(), c.getChannel().title(),
                l.getId(), l.getName(), l.getPhone() == null ? "—" : l.getPhone());
        return l.getId();
    }

    /** Ответ оператора из CRM: разговор переходит к человеку, заявка — в работу у этого сотрудника. */
    @Transactional
    public OperatorTarget operatorReply(Long leadId, String text, Long authorId, String username) {
        Conversation c = byLead(leadId);
        if (c.getMode() == ConversationMode.CLOSED) {
            throw ApiException.conflict("Разговор закрыт — клиент не получит сообщение");
        }
        c.setMode(ConversationMode.OPERATOR);
        Lead l = c.getLead();
        if (l.getStatus() == LeadStatus.NEW || l.getStatus() == LeadStatus.NEEDS_OPERATOR) {
            l.setStatus(LeadStatus.IN_PROGRESS);
        }
        if (l.getAssignedTo() == null) {
            l.setAssignedTo(users.getReferenceById(authorId));
        }
        save(c, MessageRole.OPERATOR, text, authorId);
        log.info("Заявка #{}: оператор {} ответил клиенту ({})", leadId, username, c.getChannel().title());
        return new OperatorTarget(c.getChannel(), c.getExternalChatId());
    }

    public record OperatorTarget(ChatChannel channel, String chatId) {
    }

    @Transactional
    public void returnToAssistant(Long leadId, String username) {
        Conversation c = byLead(leadId);
        if (c.getMode() != ConversationMode.OPERATOR) {
            throw ApiException.conflict("Разговор и так ведёт ИИ-менеджер");
        }
        c.setMode(ConversationMode.AI);
        log.info("Заявка #{}: разговор возвращён ИИ-менеджеру ({})", leadId, username);
    }

    /** Новые сообщения открытого разговора в чате сайта — для опроса виджетом. */
    @Transactional(readOnly = true)
    public List<ChatMessage> messagesAfter(ChatChannel channel, String chatId, long afterId) {
        return conversations.findFirstByChannelAndExternalChatIdAndModeNotOrderByIdDesc(channel, chatId,
                        ConversationMode.CLOSED)
                .map(c -> messages.findByConversationIdAndIdGreaterThanOrderById(c.getId(), afterId))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public ConversationMode mode(ChatChannel channel, String chatId) {
        return conversations.findFirstByChannelAndExternalChatIdAndModeNotOrderByIdDesc(channel, chatId,
                ConversationMode.CLOSED).map(Conversation::getMode).orElse(ConversationMode.AI);
    }

    private Conversation openConversation(ChatChannel channel, String chatId) {
        return conversations.findFirstByChannelAndExternalChatIdAndModeNotOrderByIdDesc(channel, chatId,
                ConversationMode.CLOSED).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setChannel(channel);
            c.setExternalChatId(chatId);
            conversations.saveAndFlush(c);
            log.info("Новый разговор #{} ({})", c.getId(), channel.title());
            return c;
        });
    }

    /** Открытая заявка разговора или новая, если её нет либо она уже закрыта. */
    private Lead openLead(Conversation c) {
        Lead l = c.getLead();
        if (l != null && l.getStatus().isOpen()) {
            return l;
        }
        Lead fresh = new Lead();
        fresh.setSource(c.getChannel().leadSource());
        fresh.setStatus(LeadStatus.NEW);
        fresh.setName(c.getClientName() != null ? c.getClientName()
                : c.getChannel() == ChatChannel.WEB_CHAT ? "Посетитель сайта" : "Клиент из " + c.getChannel().title());
        fresh.setPhone(c.getClientPhone());
        if (l != null && l.getPatient() != null) {
            fresh.setPatient(l.getPatient());
            fresh.setPhone(fresh.getPhone() != null ? fresh.getPhone() : l.getPhone());
            fresh.setName(l.getName());
            if (l.getAppointment() != null) {
                fresh.setSummary("Уже записан на " + DATE_TIME.format(l.getAppointment().getStartAt())
                        + " (заявка #" + l.getId() + ")");
            }
        }
        return fresh;
    }

    private Conversation byLead(Long leadId) {
        return conversations.findFirstByLeadIdOrderByLastMessageAtDesc(leadId)
                .orElseThrow(() -> ApiException.notFound("У заявки нет переписки"));
    }

    private ChatMessage save(Conversation c, MessageRole role, String text, Long authorId) {
        ChatMessage m = new ChatMessage();
        m.setConversation(c);
        m.setRole(role);
        m.setText(text);
        m.setAuthor(authorId == null ? null : users.getReferenceById(authorId));
        messages.saveAndFlush(m);
        c.setLastMessageAt(m.getSentAt());
        return m;
    }

    private Conversation find(Long id) {
        return conversations.findById(id).orElseThrow(() -> ApiException.notFound("Разговор не найден"));
    }

    private static String appendLine(String text, String line) {
        String result = text == null || text.isBlank() ? line : text + "\n" + line;
        return cut(result, 2000);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
