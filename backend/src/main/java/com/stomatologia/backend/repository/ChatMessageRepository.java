package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            select m from ChatMessage m left join fetch m.author
            where m.conversation.lead.id = :leadId
            order by m.sentAt, m.id
            """)
    List<ChatMessage> findByLeadId(@Param("leadId") Long leadId);

    /** Последние сообщения разговора, новые первыми: история для модели. */
    List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable page);

    List<ChatMessage> findByConversationIdAndIdGreaterThanOrderById(Long conversationId, Long afterId);

    long countByConversationIdAndRoleAndSentAtGreaterThanEqual(Long conversationId, MessageRole role,
                                                               LocalDateTime since);
}
