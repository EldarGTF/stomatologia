package com.stomatologia.backend.assistant;

/** Модель не ответила: сеть, таймаут, неверный ключ, перегрузка. Разговор передаётся администратору. */
public class ChatModelException extends RuntimeException {

    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
