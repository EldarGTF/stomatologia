package com.stomatologia.backend.domain;

public enum AppointmentStatus {
    SCHEDULED("Запланирован"),
    COMPLETED("Завершён"),
    CANCELLED("Отменён"),
    NO_SHOW("Неявка");

    private final String title;

    AppointmentStatus(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
