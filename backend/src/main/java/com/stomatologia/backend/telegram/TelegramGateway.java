package com.stomatologia.backend.telegram;

import com.stomatologia.backend.assistant.ChatGateway;
import com.stomatologia.backend.domain.ChatChannel;
import org.springframework.stereotype.Component;

/** Сообщения оператора из CRM — клиенту в Telegram. */
@Component
public class TelegramGateway implements ChatGateway {

    private final TelegramClient client;

    public TelegramGateway(TelegramClient client) {
        this.client = client;
    }

    @Override
    public ChatChannel channel() {
        return ChatChannel.TELEGRAM;
    }

    @Override
    public void send(String chatId, String text) {
        client.sendMessage(chatId, text, null);
    }
}
