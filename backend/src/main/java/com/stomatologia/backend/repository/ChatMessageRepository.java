package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            select m from ChatMessage m left join fetch m.author
            where m.conversation.lead.id = :leadId
            order by m.sentAt, m.id
            """)
    List<ChatMessage> findByLeadId(@Param("leadId") Long leadId);
}
