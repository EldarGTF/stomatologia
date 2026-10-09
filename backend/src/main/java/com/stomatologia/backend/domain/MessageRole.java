package com.stomatologia.backend.domain;

public enum MessageRole {
    USER,
    ASSISTANT,
    OPERATOR,
    /** Служебное: ход ИИ-менеджера с инструментами. Клиенту и в CRM не показывается. */
    TOOL
}
