package com.stomatologia.backend.domain;

public enum PaymentMethod {
    CASH("Наличные"),
    CARD("Банковская карта"),
    TRANSFER("Перевод");

    private final String title;

    PaymentMethod(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
