package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select distinct c.lead.id from Conversation c where c.lead.id in :leadIds")
    Set<Long> findLeadIdsWithConversation(@Param("leadIds") Collection<Long> leadIds);
}
