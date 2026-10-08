package com.stomatologia.client.model;

public enum Role {
    ADMIN("Администратор"),
    DOCTOR("Врач"),
    REGISTRAR("Регистратор"),
    PATIENT("Пациент");

    private final String title;

    Role(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
