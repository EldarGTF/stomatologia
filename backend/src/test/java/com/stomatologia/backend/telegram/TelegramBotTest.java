package com.stomatologia.backend.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ConversationService;
import com.stomatologia.backend.assistant.ConversationService.Reply;
import com.stomatologia.backend.assistant.ConversationStore;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.dto.PublicDtos.ClinicInfo;
import com.stomatologia.backend.service.PublicCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramBotTest {

    @Mock
    private TelegramClient client;
    @Mock
    private ConversationService conversations;
    @Mock
    private ConversationStore store;
    @Mock
    private PublicCatalogService catalog;

    private final ObjectMapper mapper = new ObjectMapper();
    private TelegramBot bot;

    @BeforeEach
    void setUp() {
        bot = new TelegramBot(client, conversations, store, catalog);
        when(catalog.clinic()).thenReturn(new ClinicInfo("Стоматология «Eldar»", "Павлодар", "+7 (777) 081-29-09",
                null, 30, 2, 24, LocalDate.now().plusDays(30)));
        when(conversations.handle(eq(ChatChannel.TELEGRAM), eq("42"), anyString(), any()))
                .thenReturn(new Reply(7L, 70L, List.of("Здравствуйте! Чем помочь?"), false));
    }

    private JsonNode update(String messageJson) throws Exception {
        return mapper.readTree("""
                {"update_id":1,"message":{"message_id":5,"chat":{"id":42,"type":"private"},
                 "from":{"id":42,"first_name":"Айгерим","last_name":"Сарсенова"},%s}}""".formatted(messageJson));
    }

    @Test
    void startGreetsAndOffersContactButton() throws Exception {
        bot.handle(update("\"text\":\"/start\""));

        verify(client).sendMessage(eq("42"), contains("Стоматология «Eldar»"), eq(TelegramBot.contactKeyboard()));
        verifyNoInteractions(conversations);
    }

    @Test
    void textGoesToAssistantAndReplyIsSent() throws Exception {
        bot.handle(update("\"text\":\"Сколько стоит чистка?\""));

        verify(client).typing("42");
        verify(conversations).handle(ChatChannel.TELEGRAM, "42", "Сколько стоит чистка?", "Айгерим Сарсенова");
        verify(client).sendMessage("42", "Здравствуйте! Чем помочь?", null);
    }

    @Test
    void ownContactIsRememberedAndKeyboardRemoved() throws Exception {
        bot.handle(update("\"contact\":{\"phone_number\":\"77015551234\",\"first_name\":\"Айгерим\",\"user_id\":42}"));

        verify(store).rememberPhone(ChatChannel.TELEGRAM, "42", "+77015551234", "Айгерим");
        verify(conversations).handle(ChatChannel.TELEGRAM, "42", "Мой номер телефона: +77015551234",
                "Айгерим Сарсенова");
        verify(client).sendMessage("42", "Здравствуйте! Чем помочь?", Map.of("remove_keyboard", true));
    }

    @Test
    void someoneElsesContactIsNotTrusted() throws Exception {
        bot.handle(update("\"contact\":{\"phone_number\":\"77009998877\",\"first_name\":\"Друг\",\"user_id\":99}"));

        verify(store, never()).rememberPhone(any(), any(), any(), any());
        verify(client).sendMessage(eq("42"), contains("свой контакт"), isNull());
    }

    @Test
    void stickersAndPhotosGetPoliteAnswer() throws Exception {
        bot.handle(update("\"sticker\":{\"file_id\":\"abc\"}"));

        verify(client).sendMessage(eq("42"), contains("только текст"), isNull());
        verifyNoInteractions(conversations);
    }

    @Test
    void groupChatsAreIgnored() throws Exception {
        bot.handle(mapper.readTree("""
                {"update_id":2,"message":{"chat":{"id":-100,"type":"group"},"from":{"id":42},"text":"Привет"}}"""));

        verifyNoInteractions(client, conversations);
    }

    @Test
    void operatorModeSendsNothing() throws Exception {
        when(conversations.handle(eq(ChatChannel.TELEGRAM), eq("42"), anyString(), any()))
                .thenReturn(new Reply(7L, 70L, List.of(), true));

        bot.handle(update("\"text\":\"Жду ответа\""));

        verify(client, never()).sendMessage(anyString(), anyString(), any());
    }

    @Test
    void withoutTokenBotDoesNotStart() {
        when(client.configured()).thenReturn(false);

        bot.start();

        org.assertj.core.api.Assertions.assertThat(bot.isRunning()).isFalse();
    }
}
