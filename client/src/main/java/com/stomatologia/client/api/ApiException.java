package com.stomatologia.client.api;

/**
 * Ошибка обращения к серверу с сообщением, пригодным для показа пользователю.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
