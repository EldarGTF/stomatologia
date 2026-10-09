package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.dto.LeadDtos.LeadDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeadDtosTest {

    private static Lead lead(long id) {
        Lead l = new Lead();
        l.setId(id);
        l.setSource(LeadSource.TELEGRAM);
        l.setStatus(LeadStatus.NEW);
        l.setName("Клиент");
        return l;
    }

    private static Conversation chatOf(Lead current) {
        Conversation c = new Conversation();
        c.setChannel(ChatChannel.TELEGRAM);
        c.setMode(ConversationMode.AI);
        c.setLead(current);
        return c;
    }

    @Test
    void currentLeadOfConversationCanReply() {
        Lead l = lead(26);

        LeadDto dto = LeadDto.from(l, chatOf(l));

        assertThat(dto.hasConversation()).isTrue();
        assertThat(dto.conversationMode()).isEqualTo(ConversationMode.AI);
    }

    @Test
    void earlierLeadOfSameChatShowsItsMessagesReadOnly() {
        LeadDto dto = LeadDto.from(lead(24), chatOf(lead(26)));

        assertThat(dto.hasConversation()).isTrue();
        assertThat(dto.conversationChannel()).isEqualTo(ChatChannel.TELEGRAM);
        assertThat(dto.conversationMode()).isEqualTo(ConversationMode.CLOSED);
    }
}
