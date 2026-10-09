package com.stomatologia.backend.domain;

/** Кто сейчас ведёт разговор: ИИ-менеджер, живой оператор или разговор закрыт. */
public enum ConversationMode {
    AI,
    OPERATOR,
    CLOSED
}
