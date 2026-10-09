package com.stomatologia.backend.service;

import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.dto.NotificationDtos.NotificationDto;
import com.stomatologia.backend.dto.NotificationDtos.NotificationFeed;
import com.stomatologia.backend.dto.NotificationDtos.NotificationType;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Уведомления CRM: новые заявки и сообщения клиентов в разговорах у оператора. Считаются по уже
 * существующим заявкам и сообщениям — курсоры это последние виденные клиентом id.
 */
@Service
public class NotificationService {

    static final int MAX_ITEMS = 20;
    private static final int TEXT_LIMIT = 140;

    private final LeadRepository leads;
    private final ChatMessageRepository messages;

    public NotificationService(LeadRepository leads, ChatMessageRepository messages) {
        this.leads = leads;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public NotificationFeed feed(Long afterLead, Long afterMessage) {
        long lastLead = leads.findMaxId();
        long lastMessage = messages.findMaxId();
        if (afterLead == null || afterMessage == null) {
            return new NotificationFeed(lastLead, lastMessage, List.of());
        }
        Long me = CurrentUser.get().id();
        List<NotificationDto> items = new ArrayList<>();
        Set<Long> newLeads = new HashSet<>();
        for (Lead l : leads.findCreatedBetween(afterLead, lastLead)) {
            newLeads.add(l.getId());
            if (l.getAssignedTo() != null && l.getAssignedTo().getId().equals(me)) {
                continue;
            }
            items.add(new NotificationDto(NotificationType.NEW_LEAD, l.getId(),
                    "Новая заявка · " + l.getSource().title(), leadText(l), l.getCreatedAt()));
        }

        Map<Long, List<ChatMessage>> byLead = new LinkedHashMap<>();
        for (ChatMessage m : messages.findClientMessagesForOperator(afterMessage, lastMessage)) {
            if (!newLeads.contains(m.getLead().getId())) {
                byLead.computeIfAbsent(m.getLead().getId(), id -> new ArrayList<>()).add(m);
            }
        }
        byLead.forEach((leadId, list) -> {
            ChatMessage last = list.get(list.size() - 1);
            String title = (list.size() == 1 ? "Сообщение клиента" : "Сообщения клиента (" + list.size() + ")")
                    + " · " + last.getConversation().getChannel().title();
            items.add(new NotificationDto(NotificationType.CLIENT_MESSAGE, leadId, title,
                    last.getLead().getName() + ": " + cut(last.getText()), last.getSentAt()));
        });

        List<NotificationDto> latest = items.size() > MAX_ITEMS
                ? items.subList(items.size() - MAX_ITEMS, items.size()) : items;
        return new NotificationFeed(lastLead, lastMessage, List.copyOf(latest));
    }

    private static String leadText(Lead l) {
        StringBuilder sb = new StringBuilder(l.getName());
        if (l.getPhone() != null) {
            sb.append(", ").append(l.getPhone());
        }
        if (l.getSummary() != null && !l.getSummary().isBlank()) {
            sb.append('\n').append(cut(l.getSummary().lines().reduce((a, b) -> b).orElse("")));
        }
        return sb.toString();
    }

    private static String cut(String text) {
        String t = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return t.length() <= TEXT_LIMIT ? t : t.substring(0, TEXT_LIMIT - 1) + "…";
    }
}
