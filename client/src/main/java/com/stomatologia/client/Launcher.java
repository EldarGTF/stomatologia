package com.stomatologia.client;

import javafx.application.Application;

/**
 * Точка входа без наследования от Application — позволяет запускать клиент из обычного classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(DentalClinicApp.class, args);
    }
}
