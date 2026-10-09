package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ChatModel.ModelMessage;
import com.stomatologia.backend.assistant.ChatModel.ModelReply;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolResults;
import com.stomatologia.backend.assistant.ChatModel.UserText;
import com.stomatologia.backend.assistant.ConversationService.Reply;
import com.stomatologia.backend.assistant.ConversationStore.ChatState;
import com.stomatologia.backend.assistant.ConversationStore.OperatorTarget;
import com.stomatologia.backend.assistant.ConversationStore.Received;
import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.dto.PublicDtos.ClinicInfo;
import com.stomatologia.backend.security.AuthUser;
import com.stomatologia.backend.service.PublicCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceTest {

    private static final String CHAT = "100500";

    @Mock
    private ChatModel model;
    @Mock
    private AssistantTools tools;
    @Mock
    private ConversationStore store;
    @Mock
    private PublicCatalogService catalog;
    @Mock
    private ChatGateway telegram;

    private ConversationService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private ConversationMode mode = ConversationMode.AI;

    @BeforeEach
    void setUp() {
        when(telegram.channel()).thenReturn(ChatChannel.TELEGRAM);
        service = new ConversationService(model, tools, store, catalog, List.of(telegram), 20, 60);
        when(catalog.clinic()).thenReturn(new ClinicInfo("Стоматология «Eldar»", "Павлодар, ул. Назарбаева, 79",
                "+7 (777) 081-29-09", null, 30, 2, 24, LocalDate.now().plusDays(30)));
        when(model.available()).thenReturn(true);
        when(store.receive(eq(ChatChannel.TELEGRAM), eq(CHAT), anyString(), any()))
                .thenAnswer(inv -> new Received(7L, 70L, mode, 1));
        when(store.state(7L)).thenAnswer(inv -> new ChatState(7L, ChatChannel.TELEGRAM, mode, "Айгерим", null,
                null, null, null));
        when(store.history(7L, 20)).thenReturn(List.of(message(MessageRole.USER, "Сколько стоит чистка?")));
        when(tools.specs()).thenReturn(List.of());
        when(store.handoff(anyLong(), anyString())).thenAnswer(inv -> {
            mode = ConversationMode.OPERATOR;
            return 12L;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ChatMessage message(MessageRole role, String text) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setText(text);
        return m;
    }

    private Reply send(String text) {
        return service.handle(ChatChannel.TELEGRAM, CHAT, text, "Айгерим");
    }

    @Test
    void plainAnswerIsSavedAndReturned() {
        when(model.reply(anyString(), anyList(), anyList())).thenReturn(new ModelReply("Чистка — **15 000 ₸**.", List.of()));

        Reply reply = send("Сколько стоит чистка?");

        assertThat(reply.texts()).containsExactly("Чистка — 15 000 ₸.");
        assertThat(reply.operator()).isFalse();
        verify(store).saveReply(7L, MessageRole.ASSISTANT, "Чистка — 15 000 ₸.", null);
    }

    @Test
    void systemPromptHasClinicDataAndRules() {
        when(model.reply(anyString(), anyList(), anyList())).thenReturn(new ModelReply("Здравствуйте!", List.of()));

        send("Привет");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(model).reply(system.capture(), anyList(), anyList());
        assertThat(system.getValue())
                .contains("Стоматология «Eldar»", "Павлодар, ул. Назарбаева, 79", "+7 (777) 081-29-09")
                .contains("Telegram", "Поделиться контактом", "103", "handoff_to_operator", "Айгерим")
                .contains("Записи через этот чат нет");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolCallsAreExecutedUntilModelAnswers() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "list_services", mapper.readTree("{}"));
        when(model.reply(anyString(), anyList(), anyList()))
                .thenReturn(new ModelReply("", List.of(call)))
                .thenReturn(new ModelReply("Чистка стоит 15 000 ₸.", List.of()));
        when(tools.execute(call, 7L)).thenReturn(new ToolResult("toolu_1", "[{\"name\":\"Чистка\"}]", false));

        Reply reply = send("Сколько стоит чистка?");

        assertThat(reply.texts()).containsExactly("Чистка стоит 15 000 ₸.");
        ArgumentCaptor<List<ModelMessage>> history = ArgumentCaptor.forClass(List.class);
        verify(model, times(2)).reply(anyString(), history.capture(), anyList());
        List<ModelMessage> second = history.getAllValues().get(1);
        assertThat(second.get(0)).isEqualTo(new UserText("Сколько стоит чистка?"));
        assertThat(second.get(second.size() - 1)).isInstanceOf(ToolResults.class);
    }

    @Test
    void bookingClaimWithoutToolIsSentBackToModel() {
        List<List<ModelMessage>> seen = new ArrayList<>();
        when(model.reply(anyString(), anyList(), anyList())).thenAnswer(inv -> {
            seen.add(List.copyOf(inv.getArgument(1)));
            return seen.size() == 1
                    ? new ModelReply("Спасибо! Вы записаны на сегодня в 15:00.", List.of())
                    : new ModelReply("Записываю на 15:00? Подтвердите согласие на обработку данных.", List.of());
        });

        Reply reply = send("Да");

        assertThat(reply.texts()).containsExactly("Записываю на 15:00? Подтвердите согласие на обработку данных.");
        assertThat(seen.get(1).get(seen.get(1).size() - 1)).isEqualTo(new UserText(ConversationService.NOT_BOOKED));
        verify(store, never()).handoff(anyLong(), anyString());
    }

    @Test
    void repeatedBookingClaimWithoutToolHandsOff() {
        when(model.reply(anyString(), anyList(), anyList()))
                .thenReturn(new ModelReply("Ваша запись оформлена!", List.of()));

        Reply reply = send("Да");

        verify(model, times(2)).reply(anyString(), anyList(), anyList());
        verify(store).handoff(eq(7L), anyString());
        assertThat(reply.texts().get(0)).contains("администратору");
    }

    @Test
    void realBookingIsAnnounced() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "book_appointment", mapper.readTree("{}"));
        when(model.reply(anyString(), anyList(), anyList()))
                .thenReturn(new ModelReply("", List.of(call)))
                .thenReturn(new ModelReply("Готово, вы записаны на сегодня в 15:00.", List.of()));
        when(tools.execute(call, 7L)).thenReturn(new ToolResult("toolu_1", "{\"when\":\"сегодня 15:00\"}", false));

        Reply reply = send("Да");

        assertThat(reply.texts()).containsExactly("Готово, вы записаны на сегодня в 15:00.");
        verify(model, times(2)).reply(anyString(), anyList(), anyList());
    }

    @Test
    void bookingClaimsAreRecognized() {
        assertThat(ConversationService.claimsBooking("Спасибо! Вы записаны на завтра")).isTrue();
        assertThat(ConversationService.claimsBooking("Записала вас к Ивановой")).isTrue();
        assertThat(ConversationService.claimsBooking("Ваша запись оформлена.")).isTrue();
        assertThat(ConversationService.claimsBooking("Записываю вас на 10:00?")).isFalse();
        assertThat(ConversationService.claimsBooking("Есть время завтра в 10:00 и 11:30")).isFalse();
    }

    @Test
    void noSlotsClaimWithoutSearchIsSentBackToModel() throws Exception {
        ToolCall search = new ToolCall("toolu_1", "find_free_slots", mapper.readTree("{\"service_id\":3}"));
        List<List<ModelMessage>> seen = new ArrayList<>();
        when(model.reply(anyString(), anyList(), anyList())).thenAnswer(inv -> {
            seen.add(List.copyOf(inv.getArgument(1)));
            return switch (seen.size()) {
                case 1 -> new ModelReply("К сожалению, запись на сегодня уже закрыта.", List.of());
                case 2 -> new ModelReply("", List.of(search));
                default -> new ModelReply("Сегодня свободно 18:00 у Петрова.", List.of());
            };
        });
        when(tools.execute(search, 7L)).thenReturn(new ToolResult("toolu_1", "[\"18:00\"]", false));

        Reply reply = send("Запишите на сегодня");

        assertThat(reply.texts()).containsExactly("Сегодня свободно 18:00 у Петрова.");
        assertThat(seen.get(1).get(seen.get(1).size() - 1)).isEqualTo(new UserText(ConversationService.NOT_SEARCHED));
    }

    @Test
    void noSlotsAfterRealSearchIsAccepted() throws Exception {
        ToolCall search = new ToolCall("toolu_1", "find_free_slots", mapper.readTree("{\"service_id\":3}"));
        when(model.reply(anyString(), anyList(), anyList()))
                .thenReturn(new ModelReply("", List.of(search)))
                .thenReturn(new ModelReply("На сегодня мест нет, ближайшее — завтра в 10:00.", List.of()));
        when(tools.execute(search, 7L)).thenReturn(new ToolResult("toolu_1", "[]", false));

        Reply reply = send("Запишите на сегодня");

        assertThat(reply.texts()).containsExactly("На сегодня мест нет, ближайшее — завтра в 10:00.");
        verify(model, times(2)).reply(anyString(), anyList(), anyList());
    }

    @Test
    void noSlotsClaimsAreRecognized() {
        assertThat(ConversationService.claimsNoSlots("Запись на этот же день вероятно уже закрыта.")).isTrue();
        assertThat(ConversationService.claimsNoSlots("На сегодня свободных мест нет")).isTrue();
        assertThat(ConversationService.claimsNoSlots("Всё время на завтра уже занято")).isTrue();
        assertThat(ConversationService.claimsNoSlots("У вас пока нет записи. Подобрать время?")).isFalse();
        assertThat(ConversationService.claimsNoSlots("Есть время завтра в 10:00 и 11:30")).isFalse();
    }

    @Test
    void toolTurnIsSavedForNextMessages() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "list_services", mapper.readTree("{}"));
        when(model.reply(anyString(), anyList(), anyList()))
                .thenReturn(new ModelReply("", List.of(call)))
                .thenReturn(new ModelReply("Чистка стоит 15 000 ₸.", List.of()));
        when(tools.execute(call, 7L)).thenReturn(new ToolResult("toolu_1", "[{\"name\":\"Чистка\"}]", false));

        send("Сколько стоит чистка?");

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(store).saveReply(eq(7L), eq(MessageRole.TOOL), saved.capture(), isNull());
        assertThat(ToolTurns.read(saved.getValue())).hasSize(2);
    }

    @Test
    void earlierToolResultsAreShownToModel() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "find_free_slots", mapper.readTree("{\"service\":\"Лечение кариеса\"}"));
        String turn = ToolTurns.write(new ChatModel.AssistantTurn(null, List.of(call)),
                List.of(new ToolResult("toolu_1", "{\"slots\":[\"15:00\"]}", false)));
        when(store.history(7L, 20)).thenReturn(List.of(
                message(MessageRole.TOOL, turn),
                message(MessageRole.ASSISTANT, "Есть 15:00"),
                message(MessageRole.USER, "Запишите на кариес"),
                message(MessageRole.TOOL, turn),
                message(MessageRole.ASSISTANT, "Есть 15:00. Подходит?"),
                message(MessageRole.USER, "Да")));
        List<List<ModelMessage>> seen = new ArrayList<>();
        when(model.reply(anyString(), anyList(), anyList())).thenAnswer(inv -> {
            seen.add(List.copyOf(inv.getArgument(1)));
            return new ModelReply("Как вас зовут?", List.of());
        });

        send("Да");

        List<ModelMessage> history = seen.get(0);
        assertThat(history.get(0)).isEqualTo(new UserText("Запишите на кариес"));
        assertThat(history.get(1)).isEqualTo(new ChatModel.AssistantTurn(null, List.of(call)));
        assertThat(history.get(2)).isInstanceOf(ToolResults.class);
        assertThat(history).hasSize(5);
    }

    @Test
    void operatorMessagesAreShownToModelAsAdministrator() {
        when(store.history(7L, 20)).thenReturn(List.of(message(MessageRole.USER, "Можно в субботу?"),
                message(MessageRole.OPERATOR, "Да, до 14:00"), message(MessageRole.USER, "Спасибо")));
        List<List<ModelMessage>> seen = new ArrayList<>();
        when(model.reply(anyString(), anyList(), anyList())).thenAnswer(inv -> {
            seen.add(List.copyOf(inv.getArgument(1)));
            return new ModelReply("Пожалуйста!", List.of());
        });

        send("Спасибо");

        assertThat(seen.get(0).get(1)).isEqualTo(ChatModel.AssistantTurn.text("Администратор: Да, до 14:00"));
    }

    @Test
    void operatorModeDoesNotCallModel() {
        mode = ConversationMode.OPERATOR;

        Reply reply = send("Когда ответите?");

        assertThat(reply.texts()).isEmpty();
        assertThat(reply.operator()).isTrue();
        verify(model, never()).reply(anyString(), anyList(), anyList());
    }

    @Test
    void withoutKeyChatGoesToAdministrator() {
        when(model.available()).thenReturn(false);

        Reply reply = send("Здравствуйте");

        verify(store).handoff(eq(7L), contains("ключ"));
        assertThat(reply.operator()).isTrue();
        assertThat(reply.texts().get(0)).contains("администратору", "+7 (777) 081-29-09");
        verify(store).saveReply(eq(7L), eq(MessageRole.ASSISTANT), contains("администратору"), isNull());
    }

    @Test
    void modelFailureHandsOffInsteadOfError() {
        when(model.reply(anyString(), anyList(), anyList())).thenThrow(new ChatModelException("Модель вернула 529"));

        Reply reply = send("Здравствуйте");

        verify(store).handoff(eq(7L), contains("недоступен"));
        assertThat(reply.operator()).isTrue();
        assertThat(reply.texts()).hasSize(1);
    }

    @Test
    void tooManyMessagesPerDayHandOff() {
        when(store.receive(eq(ChatChannel.TELEGRAM), eq(CHAT), anyString(), any()))
                .thenReturn(new Received(7L, 70L, ConversationMode.AI, 61));

        send("ещё вопрос");

        verify(store).handoff(eq(7L), contains("60"));
        verify(model, never()).reply(anyString(), anyList(), anyList());
    }

    @Test
    void endlessToolCallsAreCutOff() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "list_doctors", mapper.readTree("{}"));
        when(model.reply(anyString(), anyList(), anyList())).thenReturn(new ModelReply("", List.of(call)));
        when(tools.execute(any(), anyLong())).thenReturn(new ToolResult("toolu_1", "[]", false));

        Reply reply = send("Кто из врачей работает?");

        verify(model, times(ConversationService.MAX_TOOL_STEPS)).reply(anyString(), anyList(), anyList());
        verify(store).handoff(eq(7L), anyString());
        assertThat(reply.operator()).isTrue();
    }

    @Test
    void handoffToolSwitchesReplyToOperatorMode() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "handoff_to_operator", mapper.readTree("{\"reason\":\"жалоба\"}"));
        when(model.reply(anyString(), anyList(), anyList()))
                .thenReturn(new ModelReply("", List.of(call)))
                .thenReturn(new ModelReply("Передала администратору, он ответит здесь.", List.of()));
        when(tools.execute(call, 7L)).thenAnswer(inv -> {
            mode = ConversationMode.OPERATOR;
            return new ToolResult("toolu_1", "{}", false);
        });

        Reply reply = send("Хочу пожаловаться");

        assertThat(reply.texts()).containsExactly("Передала администратору, он ответит здесь.");
        assertThat(reply.operator()).isTrue();
    }

    @Test
    void operatorReplyIsDeliveredToMessenger() {
        login();
        when(store.operatorReply(5L, "Добрый день! Перезвоню через 5 минут", 2L, "registrar"))
                .thenReturn(new OperatorTarget(ChatChannel.TELEGRAM, CHAT));

        service.operatorReply(5L, "  Добрый день! Перезвоню через 5 минут ");

        verify(telegram).send(CHAT, "Добрый день! Перезвоню через 5 минут");
    }

    @Test
    void webChatHasNoGatewayAndNeedsNoDelivery() {
        login();
        when(store.operatorReply(eq(5L), anyString(), anyLong(), anyString()))
                .thenReturn(new OperatorTarget(ChatChannel.WEB_CHAT, "session"));

        service.operatorReply(5L, "Здравствуйте");

        verify(telegram, never()).send(anyString(), anyString());
    }

    @Test
    void failedDeliveryIsReportedToOperator() {
        login();
        when(store.operatorReply(eq(5L), anyString(), anyLong(), anyString()))
                .thenReturn(new OperatorTarget(ChatChannel.TELEGRAM, CHAT));
        doThrow(new RuntimeException("Forbidden: bot was blocked by the user"))
                .when(telegram).send(anyString(), anyString());

        assertThatThrownBy(() -> service.operatorReply(5L, "Здравствуйте"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("не доставлено")
                .hasMessageContaining("blocked");
    }

    @Test
    void markdownIsStripped() {
        assertThat(ConversationService.clean("## Цены\n**Чистка** — 15 000 ₸")).isEqualTo("Цены\nЧистка — 15 000 ₸");
        assertThat(ConversationService.clean("  ")).isNull();
    }

    private static void login() {
        AuthUser me = new AuthUser(2L, "registrar", "Козлова Марина Сергеевна", Role.REGISTRAR, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(me, null, List.of()));
    }
}
