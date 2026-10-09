package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            select m from ChatMessage m left join fetch m.author
            where m.lead.id = :leadId and m.role <> com.stomatologia.backend.domain.MessageRole.TOOL
            order by m.sentAt, m.id
            """)
    List<ChatMessage> findByLeadId(@Param("leadId") Long leadId);

    /** Последние сообщения заявки, новые первыми: история для модели. */
    List<ChatMessage> findByLeadIdOrderByIdDesc(Long leadId, Pageable page);

    /** Пары «заявка — разговор», в которых у заявки есть сообщения. */
    @Query("select distinct m.lead.id, m.conversation from ChatMessage m where m.lead.id in :leadIds")
    List<Object[]> findConversationsByLeadIds(@Param("leadIds") Collection<Long> leadIds);

    Optional<ChatMessage> findFirstByLeadIdOrderByIdDesc(Long leadId);

    List<ChatMessage> findByLeadIdAndRoleNotAndIdGreaterThanOrderById(Long leadId, MessageRole role, Long afterId);

    @Query("select coalesce(max(m.id), 0) from ChatMessage m")
    long findMaxId();

    /** Сообщения клиентов в разговорах, которые сейчас ведёт оператор, — по текущей заявке разговора. */
    @Query("""
            select m from ChatMessage m join fetch m.conversation c join fetch m.lead l
            where m.id > :after and m.id <= :last
              and m.role = com.stomatologia.backend.domain.MessageRole.USER
              and c.mode = com.stomatologia.backend.domain.ConversationMode.OPERATOR
              and c.lead = l
            order by m.id
            """)
    List<ChatMessage> findClientMessagesForOperator(@Param("after") long after, @Param("last") long last);

    long countByConversationIdAndRoleAndSentAtGreaterThanEqual(Long conversationId, MessageRole role,
                                                               LocalDateTime since);
}
