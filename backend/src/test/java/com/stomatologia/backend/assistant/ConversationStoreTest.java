package com.stomatologia.backend.assistant;

import com.stomatologia.backend.domain.ChatChannel;
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
