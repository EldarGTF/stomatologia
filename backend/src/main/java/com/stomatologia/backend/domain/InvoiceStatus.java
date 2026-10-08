package com.stomatologia.backend.domain;

public enum InvoiceStatus {
    UNPAID("Не оплачен"),
    PARTIAL("Оплачен частично"),
    PAID("Оплачен"),
    CANCELLED("Аннулирован");

    private final String title;

    InvoiceStatus(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
