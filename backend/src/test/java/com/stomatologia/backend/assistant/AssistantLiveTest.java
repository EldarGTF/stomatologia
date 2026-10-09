package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ChatModel.AssistantTurn;
import com.stomatologia.backend.assistant.ChatModel.ModelMessage;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ConversationService.Reply;
import com.stomatologia.backend.assistant.ConversationStore.ChatState;
import com.stomatologia.backend.assistant.ConversationStore.Received;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.dto.PublicDtos.BookingInfo;
import com.stomatologia.backend.dto.PublicDtos.BookingRequest;
import com.stomatologia.backend.dto.PublicDtos.ClinicInfo;
import com.stomatologia.backend.dto.PublicDtos.DoctorInfo;
import com.stomatologia.backend.dto.PublicDtos.ServiceInfo;
import com.stomatologia.backend.service.PublicBookingService;
import com.stomatologia.backend.service.PublicBookingService.OnlineBooking;
import com.stomatologia.backend.service.PublicCatalogService;
import com.stomatologia.backend.service.SlotService.DayAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверочные диалоги с настоящим Claude: данные клиники и запись подменены, модель, инструменты и логика
 * разговора — настоящие. Проверяется, какие инструменты и с какими параметрами вызывает модель.
 * Запуск: {@code mvnw -pl backend test -Dlive=true -Dtest=AssistantLiveTest} (нужен ANTHROPIC_API_KEY
 * в окружении или в .env). В CI не запускается — стоит денег и зависит от ответов модели.
 */
@EnabledIfSystemProperty(named = "live", matches = "true")
class AssistantLiveTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final List<ServiceInfo> SERVICES = List.of(
            new ServiceInfo(1L, "Консультация стоматолога", "Осмотр и план лечения", new BigDecimal("7500"), 30),
            new ServiceInfo(2L, "Профессиональная гигиена", "Чистка ультразвуком и Air Flow", new BigDecimal("22500"), 60),
            new ServiceInfo(3L, "Лечение кариеса", "Пломба светового отверждения", new BigDecimal("27500"), 60),
            new ServiceInfo(4L, "Лечение пульпита", null, new BigDecimal("45000"), 90),
            new ServiceInfo(5L, "Удаление зуба простое", null, new BigDecimal("17500"), 30),
            new ServiceInfo(7L, "Отбеливание", null, new BigDecimal("75000"), 90));
    private static final List<DoctorInfo> DOCTORS = List.of(
            new DoctorInfo(1L, "Иванова Елена Петровна", "Терапевт", "101"),
            new DoctorInfo(2L, "Петров Андрей Викторович", "Стоматолог-хирург", "102"));

    private final List<ChatMessage> chat = new ArrayList<>();
    private final List<BookingRequest> bookings = new ArrayList<>();
    private ConversationMode mode = ConversationMode.AI;
    private LocalDateTime booked;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        String key = setting("ANTHROPIC_API_KEY");
        assumeTrue(key != null, "Нет ANTHROPIC_API_KEY ни в окружении, ни в .env");
        ObjectMapper mapper = new ObjectMapper();
        String modelName = Optional.ofNullable(setting("ANTHROPIC_MODEL")).orElse("claude-haiku-4-5");
        ChatModel model = new AnthropicChatModel(mapper, key, modelName, "https://api.anthropic.com", 1024, 60);

        PublicCatalogService catalog = mock(PublicCatalogService.class);
        when(catalog.clinic()).thenReturn(new ClinicInfo("Стоматология «Eldar»", "Павлодар, ул. Назарбаева, 79",
                "+7 (777) 081-29-09", null, 30, 2, 24, TODAY.plusDays(30)));
        when(catalog.services()).thenReturn(SERVICES);
        when(catalog.doctors()).thenReturn(DOCTORS);
        when(catalog.slots(anyLong(), any(), any())).thenAnswer(inv -> slots(inv.getArgument(2)));
        when(catalog.availability(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            List<DayAvailability> days = new ArrayList<>();
            for (int i = 0; i <= 30; i++) {
                days.add(new DayAvailability(TODAY.plusDays(i), 4));
            }
            return days;
        });

        PublicBookingService booking = mock(PublicBookingService.class);
        when(booking.book(any(), any(), any())).thenAnswer(inv -> book(inv.getArgument(0)));

        ConversationStore store = mock(ConversationStore.class);
        when(store.receive(any(), anyString(), anyString(), any())).thenAnswer(inv ->
                new Received(7L, add(MessageRole.USER, inv.getArgument(2)), mode, 1));
        when(store.saveReply(eq(7L), any(), anyString(), any())).thenAnswer(inv ->
                add(inv.getArgument(1), inv.getArgument(2)));
        when(store.history(eq(7L), anyInt())).thenAnswer(inv -> {
            int limit = inv.getArgument(1);
            return List.copyOf(chat.subList(Math.max(0, chat.size() - limit), chat.size()));
        });
        when(store.state(7L)).thenAnswer(inv -> new ChatState(7L, ChatChannel.WEB_CHAT, mode, null, null, 50L,
                booked != null ? LeadStatus.BOOKED : LeadStatus.NEW, booked));
        when(store.handoff(anyLong(), anyString())).thenAnswer(inv -> {
            mode = ConversationMode.OPERATOR;
            return 50L;
        });

        AssistantTools tools = new AssistantTools(catalog, booking, store, mapper, "http://localhost:8080");
        service = new ConversationService(model, tools, store, catalog, List.of(), 30, 60);
    }

    @Test
    void bookingKeepsServiceAgreedSeveralMessagesAgo() {
        say("Здравствуйте! Хочу записаться на лечение кариеса на завтра");
        assertThat(callsInLastTurn("find_free_slots")).as("искал свободное время").isNotEmpty();
        assertThat(serviceOf(callsInLastTurn("find_free_slots").get(0))).isEqualTo("Лечение кариеса");

        say("Давайте на 15:00");
        say("Иванов Иван, +77011234567");
        for (int i = 0; i < 3 && bookings.isEmpty(); i++) {
            say(i == 0 ? "Да, всё верно, согласен на обработку данных" : "Да, записывайте");
        }

        assertThat(bookings).as("записал").hasSize(1);
        assertThat(bookings.get(0).serviceId()).as("услуга").isEqualTo(3L);
        assertThat(bookings.get(0).startAt()).isEqualTo(TOMORROW.atTime(15, 0));
        assertThat(lastReply().toLowerCase()).contains("кариес").contains("/booking/");
    }

    @Test
    void priceComesFromCatalog() {
        String reply = say("Сколько у вас стоит профессиональная чистка зубов?");

        assertThat(callsInLastTurn("list_services")).isNotEmpty();
        assertThat(reply.replace(" ", "").replace("\u00a0", "")).contains("22500");
    }

    @Test
    void todayIsCheckedBeforeAnswering() {
        String reply = say("Запишите меня на консультацию на сегодня");

        List<ToolCall> searches = callsInLastTurn("find_free_slots");
        assertThat(searches).as("проверил расписание на сегодня").isNotEmpty();
        assertThat(searches).anySatisfy(c -> assertThat(c.input().path("date").asText()).isEqualTo(TODAY.toString()));
        assertThat(ConversationService.claimsNoSlots(reply)).as(reply).isFalse();
    }

    @Test
    void humanIsCalledOnRequest() {
        say("Позовите, пожалуйста, живого администратора, хочу поговорить с человеком");

        assertThat(callsInLastTurn("handoff_to_operator")).isNotEmpty();
        assertThat(mode).isEqualTo(ConversationMode.OPERATOR);
    }

    private String say(String text) {
        Reply reply = service.handle(ChatChannel.WEB_CHAT, "live-test", text, null);
        String answer = String.join("\n", reply.texts());
        System.out.println("КЛИЕНТ: " + text);
        callsInLastTurn(null).forEach(c -> System.out.println("  [" + c.name() + " " + c.input() + "]"));
        System.out.println("БОТ: " + answer + "\n");
        return answer;
    }

    /** Вызовы инструментов после последнего сообщения клиента; name == null — все. */
    private List<ToolCall> callsInLastTurn(String name) {
        List<ToolCall> calls = new ArrayList<>();
        for (int i = chat.size() - 1; i >= 0 && chat.get(i).getRole() != MessageRole.USER; i--) {
            if (chat.get(i).getRole() == MessageRole.TOOL) {
                for (ModelMessage m : ToolTurns.read(chat.get(i).getText())) {
                    if (m instanceof AssistantTurn turn) {
                        turn.toolCalls().stream().filter(c -> name == null || c.name().equals(name))
                                .forEach(c -> calls.add(0, c));
                    }
                }
            }
        }
        return calls;
    }

    private String lastReply() {
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (chat.get(i).getRole() == MessageRole.ASSISTANT) {
                return chat.get(i).getText();
            }
        }
        return "";
    }

    private static String serviceOf(ToolCall call) {
        ServiceInfo s = AssistantTools.match(SERVICES, ServiceInfo::name, call.input().path("service").asText());
        return s == null ? null : s.name();
    }

    private Long add(MessageRole role, String text) {
        ChatMessage m = new ChatMessage();
        m.setId((long) chat.size() + 1);
        m.setRole(role);
        m.setText(text);
        chat.add(m);
        return m.getId();
    }

    private static List<SlotDto> slots(LocalDate date) {
        List<SlotDto> list = new ArrayList<>();
        List<LocalTime> times = date.equals(TODAY)
                ? List.of(LocalTime.of(19, 0), LocalTime.of(19, 30))
                : List.of(LocalTime.of(10, 0), LocalTime.of(11, 30), LocalTime.of(15, 0), LocalTime.of(16, 30));
        for (LocalTime t : times) {
            DoctorInfo d = DOCTORS.get(t.getHour() % 2);
            list.add(new SlotDto(d.id(), d.fullName(), d.roomNumber(), date.atTime(t), date.atTime(t).plusHours(1)));
        }
        return list;
    }

    private OnlineBooking book(BookingRequest r) {
        bookings.add(r);
        booked = r.startAt();
        ServiceInfo s = SERVICES.stream().filter(x -> x.id().equals(r.serviceId())).findFirst().orElseThrow();
        DoctorInfo d = DOCTORS.get(r.startAt().getHour() % 2);
        Lead lead = new Lead();
        lead.setId(50L);
        return new OnlineBooking(lead, new BookingInfo("live-token", AppointmentStatus.SCHEDULED, "Запланирована",
                r.startAt(), r.startAt().plusMinutes(s.durationMinutes()), d.fullName(), d.specialtyName(),
                d.roomNumber(), s.name(), s.price(), r.lastName() + " " + r.firstName(), true,
                r.startAt().minusHours(24), false));
    }

    /** Переменная окружения или строка из локального .env (ключ в репозиторий не попадает). */
    private static String setting(String name) {
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        for (Path p : List.of(Path.of(".env"), Path.of("..", ".env"))) {
            if (!Files.exists(p)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(p)) {
                    if (line.startsWith(name + "=") && line.length() > name.length() + 1) {
                        return line.substring(name.length() + 1).trim();
                    }
                }
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
}
