package com.stomatologia.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Разговор с клиентом в мессенджере или чате сайта. */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatChannel channel;

    /** Идентификатор чата во внешней системе (chat_id в Telegram, номер в WhatsApp). */
    @Column(nullable = false, length = 100)
    private String externalChatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationMode mode = ConversationMode.AI;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime lastMessageAt = LocalDateTime.now();
}
