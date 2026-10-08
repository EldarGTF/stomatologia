package com.stomatologia.client;

import javafx.application.Application;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Точка входа без наследования от Application — позволяет запускать клиент из обычного classpath.
 */
public final class Launcher {

    private static final Logger log = LogManager.getLogger(Launcher.class);

    private Launcher() {
    }

    public static void main(String[] args) {
        log.info("Запуск клиента: Java {}, сервер {}", System.getProperty("java.version"),
                System.getProperty("api.url", "http://localhost:8080"));
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
                log.error("Необработанная ошибка в потоке {}", thread.getName(), ex));
        Application.launch(DentalClinicApp.class, args);
        log.info("Клиент закрыт");
    }
}
