package com.stomatologia.backend.service;

import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.dto.NotificationDtos.NotificationDto;
import com.stomatologia.backend.dto.NotificationDtos.NotificationFeed;
import com.stomatologia.backend.dto.NotificationDtos.NotificationType;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private LeadRepository leads;
    @Mock
    private ChatMessageRepository messages;
    @InjectMocks
    private NotificationService service;

    @BeforeEach
    void setUp() {
        AuthUser me = new AuthUser(2L, "registrar", "Козлова Марина Сергеевна", Role.REGISTRAR, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(me, null, List.of()));
        when(leads.findMaxId()).thenReturn(12L);
        when(messages.findMaxId()).thenReturn(80L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Lead lead(long id, LeadSource source, String name, String summary, Long assignedTo) {
        Lead l = new Lead();
        l.setId(id);
        l.setSource(source);
        l.setName(name);
        l.setSummary(summary);
        if (assignedTo != null) {
            User u = new User();
            u.setId(assignedTo);
            l.setAssignedTo(u);
        }
        return l;
    }

    private static ChatMessage message(long id, Lead lead, ChatChannel channel, String text) {
        Conversation c = new Conversation();
        c.setChannel(channel);
        c.setLead(lead);
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setConversation(c);
        m.setLead(lead);
        m.setRole(MessageRole.USER);
        m.setText(text);
        return m;
    }

    @Test
    void firstRequestOnlyReturnsCursors() {
        NotificationFeed feed = service.feed(null, null);

        assertThat(feed.lastLeadId()).isEqualTo(12L);
        assertThat(feed.lastMessageId()).isEqualTo(80L);
        assertThat(feed.items()).isEmpty();
        verify(leads, never()).findCreatedBetween(anyLong(), anyLong());
    }

    @Test
    void newLeadsExceptOwnPhoneLeads() {
        when(leads.findCreatedBetween(10L, 12L)).thenReturn(List.of(
                lead(11L, LeadSource.TELEGRAM, "Айгерим", "Написал в Telegram: болит зуб", null),
                lead(12L, LeadSource.PHONE, "Иванов", "Позвонил", 2L)));

        NotificationFeed feed = service.feed(10L, 80L);

        assertThat(feed.items()).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.NEW_LEAD);
            assertThat(n.leadId()).isEqualTo(11L);
            assertThat(n.title()).isEqualTo("Новая заявка · Telegram");
            assertThat(n.text()).isEqualTo("Айгерим\nНаписал в Telegram: болит зуб");
        });
        assertThat(feed.lastLeadId()).isEqualTo(12L);
    }

    @Test
    void clientMessagesAreGroupedByLeadAndSkippedForNewLeads() {
        Lead old = lead(5L, LeadSource.WEBSITE, "Гость сайта", null, null);
        Lead fresh = lead(12L, LeadSource.WEBSITE, "Новый гость", null, null);
        when(leads.findCreatedBetween(11L, 12L)).thenReturn(List.of(fresh));
        when(messages.findClientMessagesForOperator(70L, 80L)).thenReturn(List.of(
                message(71L, old, ChatChannel.WEB_CHAT, "Здравствуйте"),
                message(72L, fresh, ChatChannel.WEB_CHAT, "Первое сообщение"),
                message(75L, old, ChatChannel.WEB_CHAT, "Можно   на завтра?")));

        List<NotificationDto> items = service.feed(11L, 70L).items();

        assertThat(items).extracting(NotificationDto::leadId).containsExactly(12L, 5L);
        assertThat(items.get(1).title()).isEqualTo("Сообщения клиента (2) · чат на сайте");
        assertThat(items.get(1).text()).isEqualTo("Гость сайта: Можно на завтра?");
    }

    @Test
    void longTextIsCutAndOnlyLatestItemsReturned() {
        List<Lead> many = new ArrayList<>();
        for (long id = 1; id <= NotificationService.MAX_ITEMS + 5; id++) {
            many.add(lead(id, LeadSource.WEBSITE, "Гость " + id, "x".repeat(300), null));
        }
        when(leads.findCreatedBetween(0L, 12L)).thenReturn(many);

        List<NotificationDto> items = service.feed(0L, 80L).items();

        assertThat(items).hasSize(NotificationService.MAX_ITEMS);
        assertThat(items.get(items.size() - 1).leadId()).isEqualTo(NotificationService.MAX_ITEMS + 5L);
        assertThat(items.get(0).text().lines().toList().get(1)).hasSize(140).endsWith("…");
    }
}
