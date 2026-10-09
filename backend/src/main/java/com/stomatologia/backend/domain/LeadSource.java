package com.stomatologia.backend.domain;

public enum LeadSource {
    TELEGRAM("Telegram", AppointmentSource.MESSENGER),
    WHATSAPP("WhatsApp", AppointmentSource.MESSENGER),
    WEBSITE("Сайт", AppointmentSource.WEBSITE),
    PHONE("Телефон", AppointmentSource.REGISTRY);

    private final String title;
    private final AppointmentSource appointmentSource;

    LeadSource(String title, AppointmentSource appointmentSource) {
        this.title = title;
        this.appointmentSource = appointmentSource;
    }

    public String title() {
        return title;
    }

    /** Каким источником помечается приём, созданный по заявке. */
    public AppointmentSource appointmentSource() {
        return appointmentSource;
    }
}
