package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolSpec;
import com.stomatologia.backend.assistant.ConversationStore.ChatState;
import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.dto.PublicDtos.BookingInfo;
import com.stomatologia.backend.dto.PublicDtos.BookingRequest;
import com.stomatologia.backend.dto.PublicDtos.DoctorInfo;
import com.stomatologia.backend.dto.PublicDtos.ServiceInfo;
import com.stomatologia.backend.service.PublicBookingService;
import com.stomatologia.backend.service.PublicBookingService.OnlineBooking;
import com.stomatologia.backend.service.PublicCatalogService;
import com.stomatologia.backend.service.SlotService.DayAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantToolsTest {

    private static final LocalDate DAY = LocalDate.now().plusDays(2);

    @Mock
    private PublicCatalogService catalog;
    @Mock
    private PublicBookingService booking;
    @Mock
    private ConversationStore store;

    private final ObjectMapper mapper = new ObjectMapper();
    private AssistantTools tools;

    @BeforeEach
    void setUp() {
        tools = new AssistantTools(catalog, booking, store, mapper, "https://eldar-dent.kz/");
        when(store.state(7L)).thenReturn(chat(null, null));
        when(catalog.services()).thenReturn(List.of(
                new ServiceInfo(2L, "Профессиональная гигиена", null, new BigDecimal("22500.00"), 60),
                new ServiceInfo(5L, "Лечение кариеса", "Пломба", new BigDecimal("27500.00"), 60),
                new ServiceInfo(4L, "Лечение пульпита", null, new BigDecimal("45000.00"), 90)));
        when(catalog.doctors()).thenReturn(List.of(
                new DoctorInfo(2L, "Иванова Елена Петровна", "Терапевт", "101"),
                new DoctorInfo(3L, "Петров Андрей Викторович", "Стоматолог-хирург", "102")));
        when(store.history(eq(7L), anyInt())).thenReturn(List.of(
                message(MessageRole.USER, "Хочу на кариес"),
                message(MessageRole.ASSISTANT, "Записываю? Подтвердите также согласие на обработку персональных данных"),
                message(MessageRole.USER, "Да")));
    }

    private static ChatMessage message(MessageRole role, String text) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setText(text);
        return m;
    }

    private static ChatState chat(String phone, Long leadId) {
        return new ChatState(7L, ChatChannel.TELEGRAM, ConversationMode.AI, "Айгерим", phone, leadId, null, null);
    }

    private ToolResult run(String name, String input) throws Exception {
        return tools.execute(new ToolCall("toolu_1", name, mapper.readTree(input)), 7L);
    }

    private JsonNode json(ToolResult r) throws Exception {
        assertThat(r.error()).as(r.content()).isFalse();
        return mapper.readTree(r.content());
    }

    private static SlotDto slot(long doctorId, String doctor, LocalDate day, int hour, int minute) {
        LocalDateTime start = day.atTime(hour, minute);
        return new SlotDto(doctorId, doctor, "101", start, start.plusMinutes(30));
    }

    @Test
    void everyToolHasObjectSchema() {
        assertThat(tools.specs()).extracting(ToolSpec::name).containsExactly("get_clinic_info", "list_services",
                "list_doctors", "find_free_slots", "book_appointment", "create_lead", "handoff_to_operator");
        assertThat(tools.specs()).allSatisfy(s -> assertThat(s.inputSchema()).containsEntry("type", "object"));
    }

    @Test
    void servicesHavePricesInTengeAndNoIds() throws Exception {
        JsonNode list = json(run("list_services", "{}"));

        assertThat(list.get(1).path("name").asText()).isEqualTo("Лечение кариеса");
        assertThat(list.get(1).path("price").asText()).isEqualTo("27 500 ₸");
        assertThat(list.get(1).path("duration_minutes").asInt()).isEqualTo(60);
        assertThat(list.get(1).has("id")).isFalse();
    }

    @Test
    void slotsForAnyDoctorAreUniqueByTime() throws Exception {
        when(catalog.slots(5L, null, DAY)).thenReturn(List.of(
                slot(2L, "Иванова Е. П.", DAY, 10, 0), slot(3L, "Петров А. В.", DAY, 10, 0),
                slot(3L, "Петров А. В.", DAY, 10, 30)));

        JsonNode result = json(run("find_free_slots", "{\"service\":\"Лечение кариеса\",\"date\":\"" + DAY + "\"}"));

        assertThat(result.path("service").asText()).isEqualTo("Лечение кариеса");
        JsonNode slots = result.path("slots");
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).path("start_at").asText()).isEqualTo(DAY + "T10:00");
        assertThat(slots.get(0).path("doctor").asText()).isEqualTo("Иванова Е. П.");
        assertThat(slots.get(1).path("time").asText()).isEqualTo("10:30");
    }

    @Test
    void serviceIsFoundByNameAsModelWritesIt() {
        List<ServiceInfo> all = catalog.services();
        assertThat(AssistantTools.match(all, ServiceInfo::name, "лечение кариеса").id()).isEqualTo(5L);
        assertThat(AssistantTools.match(all, ServiceInfo::name, "кариес").id()).isEqualTo(5L);
        assertThat(AssistantTools.match(all, ServiceInfo::name, "Профессиональная чистка").id()).isEqualTo(2L);
        assertThat(AssistantTools.match(all, ServiceInfo::name, "лечение зуба")).isNull();
        assertThat(AssistantTools.match(all, ServiceInfo::name, "отбеливание")).isNull();
    }

    @Test
    void ambiguousServiceIsReturnedToModelWithList() throws Exception {
        ToolResult r = run("book_appointment", """
                {"service":"лечение","start":"2030-01-10T10:00","last_name":"А","first_name":"Б",
                 "phone":"+77015551234","consent":true}""");

        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("Лечение кариеса", "Лечение пульпита", "Профессиональная гигиена");
        verify(booking, never()).book(any(), any(), any());
    }

    @Test
    void withoutDateNearestDaysAreSuggested() throws Exception {
        LocalDate today = LocalDate.now();
        List<DayAvailability> days = List.of(new DayAvailability(today, 0), new DayAvailability(today.plusDays(1), 4),
                new DayAvailability(today.plusDays(2), 2), new DayAvailability(today.plusDays(3), 1),
                new DayAvailability(today.plusDays(4), 5));
        when(catalog.availability(5L, 2L, today, today.plusDays(AssistantTools.SEARCH_DAYS))).thenReturn(days);
        when(catalog.slots(eq(5L), eq(2L), any())).thenAnswer(inv ->
                List.of(slot(2L, "Иванова Е. П.", inv.getArgument(2), 9, 0)));

        JsonNode result = json(run("find_free_slots", "{\"service\":\"кариес\",\"doctor\":\"Иванова\"}"));

        assertThat(result.path("days")).hasSize(AssistantTools.DAYS_TO_SUGGEST);
        assertThat(result.path("days").get(0).path("date").asText()).isEqualTo(today.plusDays(1).toString());
        verify(catalog, never()).slots(5L, 2L, today);
    }

    @Test
    void bookingUsesPhoneFromTelegramContactAndLinksLead() throws Exception {
        when(store.state(7L)).thenReturn(chat("+77015551234", 31L));
        LocalDateTime start = DAY.atTime(11, 30);
        Lead lead = new Lead();
        lead.setId(31L);
        when(booking.book(any(), eq(LeadSource.TELEGRAM), eq(31L))).thenReturn(new OnlineBooking(lead,
                new BookingInfo("tok123", AppointmentStatus.SCHEDULED, "Запланирована", start, start.plusHours(1),
                        "Иванова Елена Петровна", "Терапевт", "101", "Лечение кариеса", new BigDecimal("27500"),
                        "Сарсенова Айгерим", true, start.minusHours(24), false)));

        JsonNode result = json(run("book_appointment", """
                {"service":"Лечение кариеса","doctor":"Иванова","start":"%s","last_name":"Сарсенова",
                 "first_name":"Айгерим","comment":"болит зуб","consent":true}""".formatted(start.toString())));

        ArgumentCaptor<BookingRequest> r = ArgumentCaptor.forClass(BookingRequest.class);
        verify(booking).book(r.capture(), eq(LeadSource.TELEGRAM), eq(31L));
        assertThat(r.getValue().serviceId()).isEqualTo(5L);
        assertThat(r.getValue().doctorId()).isEqualTo(2L);
        assertThat(r.getValue().phone()).isEqualTo("+77015551234");
        assertThat(r.getValue().startAt()).isEqualTo(start);
        assertThat(r.getValue().consent()).isTrue();
        verify(store).linkLead(7L, 31L);
        assertThat(result.path("link").asText()).isEqualTo("https://eldar-dent.kz/booking/tok123");
        assertThat(result.path("price").asText()).isEqualTo("27 500 ₸");
        assertThat(result.path("when").asText()).contains("11:30–12:30");
    }

    @Test
    void bookingWithoutConsentIsRefused() throws Exception {
        ToolResult r = run("book_appointment", """
                {"service":"Лечение кариеса","start":"2030-01-10T10:00","last_name":"А","first_name":"Б","phone":"+77015551234",
                 "consent":false}""");

        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("согласия");
        verify(booking, never()).book(any(), any(), any());
    }

    @Test
    void consentFlagWithoutQuestionToClientIsRefused() throws Exception {
        when(store.history(eq(7L), anyInt())).thenReturn(List.of(
                message(MessageRole.ASSISTANT, "Напишите имя и телефон"),
                message(MessageRole.USER, "Иванов Иван, +77011234567")));

        ToolResult booking = run("book_appointment", """
                {"service":"Лечение кариеса","start":"2030-01-10T10:00","last_name":"Иванов","first_name":"Иван",
                 "phone":"+77011234567","consent":true}""");
        ToolResult lead = run("create_lead", """
                {"name":"Иван","phone":"+77011234567","summary":"Перезвонить","consent":true}""");

        assertThat(booking.error()).isTrue();
        assertThat(booking.content()).contains("Подтвердите также согласие");
        assertThat(lead.error()).isTrue();
        verify(this.booking, never()).book(any(), any(), any());
        verify(store, never()).callbackLead(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void consentCountsOnlyWhenClientAnsweredTheQuestion() {
        ChatMessage question = message(MessageRole.ASSISTANT, "Подтвердите согласие на обработку Персональных данных");

        assertThat(AssistantTools.consentAsked(List.of(question))).isFalse();
        assertThat(AssistantTools.consentAsked(List.of(question, message(MessageRole.USER, "Согласен")))).isTrue();
        assertThat(AssistantTools.consentAsked(List.of(message(MessageRole.USER, "Согласен на обработку персональных данных"),
                message(MessageRole.ASSISTANT, "Хорошо")))).isFalse();
    }

    @Test
    void bookingWithoutPhoneAsksForIt() throws Exception {
        ToolResult r = run("book_appointment", """
                {"service":"Лечение кариеса","start":"2030-01-10T10:00","last_name":"А","first_name":"Б","consent":true}""");

        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("телефон");
    }

    @Test
    void takenTimeIsReportedToModel() throws Exception {
        when(booking.book(any(), any(), any())).thenThrow(ApiException.conflict("Это время уже заняли"));

        ToolResult r = run("book_appointment", """
                {"service":"Лечение кариеса","start":"2030-01-10T10:00","last_name":"А","first_name":"Б","phone":"+77015551234",
                 "consent":true}""");

        assertThat(r.error()).isTrue();
        assertThat(r.content()).isEqualTo("Это время уже заняли");
        verify(store, never()).linkLead(anyLong(), anyLong());
    }

    @Test
    void wrongDateFormatIsExplained() throws Exception {
        ToolResult r = run("find_free_slots", "{\"service\":\"Лечение кариеса\",\"date\":\"10 января\"}");

        assertThat(r.error()).isTrue();
        assertThat(r.content()).contains("YYYY-MM-DD");
    }

    @Test
    void callbackLeadNeedsPhone() throws Exception {
        ToolResult r = run("create_lead", "{\"name\":\"Айгерим\",\"summary\":\"Хочет на имплантацию\",\"consent\":true}");

        assertThat(r.error()).isTrue();
        verify(store, never()).callbackLead(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void callbackLeadIsCreated() throws Exception {
        when(store.callbackLead(eq(7L), eq("Айгерим"), eq("87015551234"), isNull(), eq("после 18:00"), anyString()))
                .thenReturn(44L);

        JsonNode result = json(run("create_lead", """
                {"name":"Айгерим","phone":"87015551234","preferred_time":"после 18:00",
                 "summary":"Хочет на имплантацию","consent":true}"""));

        assertThat(result.path("lead_id").asLong()).isEqualTo(44L);
    }

    @Test
    void handoffSwitchesConversationToOperator() throws Exception {
        json(run("handoff_to_operator", "{\"reason\":\"Жалоба на врача\"}"));

        verify(store).handoff(7L, "Жалоба на врача");
    }

    @Test
    void unknownToolIsAnError() throws Exception {
        assertThat(run("delete_everything", "{}").error()).isTrue();
    }
}
