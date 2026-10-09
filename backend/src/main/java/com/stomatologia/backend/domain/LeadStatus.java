package com.stomatologia.backend.domain;

import java.util.EnumSet;
import java.util.Set;

public enum LeadStatus {
    NEW("Новая"),
    IN_PROGRESS("В работе"),
    NEEDS_OPERATOR("Нужен оператор"),
    BOOKED("Записан"),
    REJECTED("Отказ");

    /** Заявки, которые ещё ждут действий регистратора. */
    public static final Set<LeadStatus> OPEN = EnumSet.of(NEW, IN_PROGRESS, NEEDS_OPERATOR);

    private final String title;

    LeadStatus(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
