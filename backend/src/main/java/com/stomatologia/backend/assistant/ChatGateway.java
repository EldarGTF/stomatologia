package com.stomatologia.backend.assistant;

import com.stomatologia.backend.domain.ChatChannel;

/**
 * Доставка сообщений оператора в мессенджер. Чат сайта шлюза не имеет: виджет сам забирает новые сообщения.
 */
public interface ChatGateway {

    ChatChannel channel();

    /** @throws RuntimeException сообщение не доставлено */
    void send(String chatId, String text);
}
