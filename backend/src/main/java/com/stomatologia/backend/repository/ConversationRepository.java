package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.ConversationMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select c from Conversation c where c.lead.id in :leadIds")
    List<Conversation> findByLeadIds(@Param("leadIds") Collection<Long> leadIds);

    /** Открытый разговор в чате: в одном чате одновременно открыт только один (индекс uq_conversations_open). */
    Optional<Conversation> findFirstByChannelAndExternalChatIdAndModeNotOrderByIdDesc(
            ChatChannel channel, String externalChatId, ConversationMode mode);

    Optional<Conversation> findFirstByLeadIdOrderByLastMessageAtDesc(Long leadId);
}
