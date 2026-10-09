package com.stomatologia.backend.assistant;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.ConversationRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationStoreTest {

    @Mock
    private ConversationRepository conversations;
    @Mock
    private ChatMessageRepository messages;
    @Mock
    private LeadRepository leads;
    @Mock
    private ClinicServiceRepository services;
    @Mock
    private UserRepository users;

    private ConversationStore store;

    @BeforeEach
    void setUp() {
        store = new ConversationStore(conversations, messages, leads, services, users);
    }

    private void existing(Conversation c) {
        when(conversations.findFirstByChannelAndExternalChatIdAndModeNotOrderByIdDesc(eq(c.getChannel()),
                eq(c.getExternalChatId()), eq(ConversationMode.CLOSED))).thenReturn(Optional.of(c));
    }

    @Test
    void firstMessageCreatesLeadVisibleInCrm() {
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.TELEGRAM);
        c.setExternalChatId("100500");
        existing(c);

        store.receive(ChatChannel.TELEGRAM, "100500", "Сколько стоит чистка?", "Айгерим");

        ArgumentCaptor<Lead> lead = ArgumentCaptor.forClass(Lead.class);
        verify(leads).saveAndFlush(lead.capture());
        assertThat(lead.getValue().getSource()).isEqualTo(LeadSource.TELEGRAM);
        assertThat(lead.getValue().getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.getValue().getName()).isEqualTo("Айгерим");
        assertThat(lead.getValue().getSummary()).contains("Telegram", "Сколько стоит чистка?");
        assertThat(c.getLead()).isSameAs(lead.getValue());
    }

    @Test
    void webChatVisitorGetsPlaceholderNameAndPhoneFromChat() {
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.WEB_CHAT);
        c.setExternalChatId("session");
        c.setClientPhone("+77770812909");
        existing(c);

        store.receive(ChatChannel.WEB_CHAT, "session", "Запишите на консультацию", null);

        ArgumentCaptor<Lead> lead = ArgumentCaptor.forClass(Lead.class);
        verify(leads).saveAndFlush(lead.capture());
        assertThat(lead.getValue().getSource()).isEqualTo(LeadSource.WEBSITE);
        assertThat(lead.getValue().getName()).isEqualTo("Посетитель сайта");
        assertThat(lead.getValue().getPhone()).isEqualTo("+77770812909");
    }

    @Test
    void messageBelongsToLeadOfItsConversation() {
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.TELEGRAM);
        c.setExternalChatId("100500");
        existing(c);

        store.receive(ChatChannel.TELEGRAM, "100500", "Здравствуйте", "Айгерим");

        ArgumentCaptor<ChatMessage> message = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).saveAndFlush(message.capture());
        assertThat(message.getValue().getLead()).isNotNull().isSameAs(c.getLead());
    }

    @Test
    void afterRejectionNewRequestGetsNewLeadWithoutOldData() {
        Lead old = new Lead();
        old.setId(24L);
        old.setStatus(LeadStatus.REJECTED);
        old.setName("Эльдар Тохтаров");
        old.setPhone("+77770812909");
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.WEB_CHAT);
        c.setExternalChatId("session");
        c.setLead(old);
        existing(c);

        store.receive(ChatChannel.WEB_CHAT, "session", "Хочу на чистку", null);

        assertThat(c.getLead()).isNotSameAs(old);
        assertThat(c.getLead().getName()).isEqualTo("Посетитель сайта");
        assertThat(c.getLead().getPhone()).isNull();
        assertThat(c.getLead().getPatient()).isNull();
    }

    @Test
    void bookedClientWritingLaterStartsNewLeadWithReference() {
        Appointment visit = new Appointment();
        visit.setStartAt(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));
        Lead old = new Lead();
        old.setId(26L);
        old.setStatus(LeadStatus.BOOKED);
        old.setName("Русик Пупскин");
        old.setAppointment(visit);
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.TELEGRAM);
        c.setExternalChatId("100500");
        c.setLead(old);
        c.setLastMessageAt(LocalDateTime.now().minus(ConversationStore.FOLLOW_UP).minusMinutes(1));
        existing(c);

        store.receive(ChatChannel.TELEGRAM, "100500", "Хочу записать жену", "Эльдар");

        assertThat(c.getLead()).isNotSameAs(old);
        assertThat(c.getLead().getName()).isEqualTo("Эльдар");
        assertThat(c.getLead().getSummary()).contains("Русик Пупскин", "заявка #26", "Хочу записать жену");
    }

    @Test
    void nextMessagesKeepTheSameLead() {
        Lead l = new Lead();
        l.setStatus(LeadStatus.BOOKED);
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.TELEGRAM);
        c.setExternalChatId("100500");
        c.setLead(l);
        existing(c);

        store.receive(ChatChannel.TELEGRAM, "100500", "Спасибо!", "Айгерим");

        verify(leads, never()).saveAndFlush(any());
        assertThat(c.getLead()).isSameAs(l);
    }
}
