package com.stomatologia.backend.domain;

public enum AppointmentSource {
    REGISTRY("Регистратура"),
    PATIENT_ACCOUNT("Личный кабинет"),
    WEBSITE("Сайт"),
    MESSENGER("Мессенджер");

    private final String title;

    AppointmentSource(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
