package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolSpec;
import com.stomatologia.backend.assistant.ConversationStore.ChatState;
import com.stomatologia.backend.common.ApiException;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Инструменты ИИ-менеджера. Данные берутся из тех же сервисов, что у сайта онлайн-записи, поэтому модель
 * видит только активные услуги и врачей, а запись проходит все проверки расписания.
 * Ошибка инструмента возвращается модели текстом — она объясняет её клиенту своими словами.
 */
@Component
public class AssistantTools {

    private static final Logger log = LogManager.getLogger(AssistantTools.class);
    static final Locale RU = Locale.of("ru");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEEE, d MMMM, HH:mm", RU);
    private static final DateTimeFormatter ISO_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    static final int SLOTS_PER_DAY = 8;
    static final int DAYS_TO_SUGGEST = 3;
    static final int SEARCH_DAYS = 30;
    private static final String SERVICE_HINT = "название услуги точно как в list_services, например «Лечение кариеса»";

    private final PublicCatalogService catalog;
    private final PublicBookingService booking;
    private final ConversationStore store;
    private final ObjectMapper json;
    private final String siteUrl;

    public AssistantTools(PublicCatalogService catalog, PublicBookingService booking, ConversationStore store,
                          ObjectMapper json, @Value("${app.site-url:http://localhost:8080}") String siteUrl) {
        this.catalog = catalog;
        this.booking = booking;
        this.store = store;
        this.json = json;
        this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    /** Неверные параметры вызова: модель получит текст ошибки и переспросит клиента. */
    static class ToolInputException extends RuntimeException {
        ToolInputException(String message) {
            super(message);
        }
    }

    public List<ToolSpec> specs() {
        return List.of(
                new ToolSpec("get_clinic_info", "Адрес, телефон, e-mail клиники и правила онлайн-записи.", schema(Map.of())),
                new ToolSpec("list_services", "Услуги клиники: название, описание, цена в тенге, длительность.",
                        schema(Map.of())),
                new ToolSpec("list_doctors", "Врачи, которые ведут приём: ФИО, специальность, кабинет.",
                        schema(Map.of())),
                new ToolSpec("find_free_slots", """
                        Свободное время для записи на услугу. Без date — ближайшие дни со свободными окнами; \
                        с date — окна в этот день. Без doctor — у любого врача.""",
                        schema(props(
                                "service", prop("string", SERVICE_HINT),
                                "doctor", prop("string", "фамилия врача, если клиент выбрал врача"),
                                "date", prop("string", "дата в формате YYYY-MM-DD")), "service")),
                new ToolSpec("book_appointment", """
                        Записать клиента на приём. Вызывать только после того, как клиент подтвердил детали \
                        и дал согласие на обработку персональных данных. start — значение start_at из find_free_slots. \
                        Услуга — та, которую клиент подтвердил.""",
                        schema(props(
                                "service", prop("string", SERVICE_HINT),
                                "doctor", prop("string", "фамилия врача из выбранного окна"),
                                "start", prop("string", "начало приёма, YYYY-MM-DDTHH:MM"),
                                "last_name", prop("string", "фамилия пациента"),
                                "first_name", prop("string", "имя пациента"),
                                "phone", prop("string", "телефон пациента"),
                                "comment", prop("string", "жалоба или пожелание клиента, кратко"),
                                "consent", prop("boolean", "клиент согласился на обработку персональных данных")),
                                "service", "start", "last_name", "first_name", "consent")),
                new ToolSpec("create_lead", """
                        Заявка на обратный звонок: подходящего времени нет, клиент хочет обсудить с администратором \
                        или просит перезвонить. Нужны имя, телефон и согласие на обработку персональных данных.""",
                        schema(props(
                                "name", prop("string", "как обращаться к клиенту"),
                                "phone", prop("string", "телефон клиента"),
                                "service", prop("string", "название услуги, если понятна"),
                                "preferred_time", prop("string", "когда удобно, словами клиента"),
                                "summary", prop("string", "суть обращения в одном-двух предложениях"),
                                "consent", prop("boolean", "клиент согласился на обработку персональных данных")),
                                "name", "summary", "consent")),
                new ToolSpec("handoff_to_operator", """
                        Передать разговор администратору: клиент просит живого человека, жалуется, \
                        вопрос не о клинике или ты не можешь помочь. После вызова администратор ответит в этом чате.""",
                        schema(props("reason", prop("string", "почему нужен администратор, кратко")), "reason")));
    }

    public ToolResult execute(ToolCall call, Long conversationId) {
        try {
            Object result = switch (call.name()) {
                case "get_clinic_info" -> clinicInfo();
                case "list_services" -> services();
                case "list_doctors" -> doctors();
                case "find_free_slots" -> freeSlots(call.input());
                case "book_appointment" -> book(call.input(), conversationId);
                case "create_lead" -> createLead(call.input(), conversationId);
                case "handoff_to_operator" -> handoff(call.input(), conversationId);
                default -> throw new ToolInputException("Неизвестный инструмент " + call.name());
            };
            return new ToolResult(call.id(), json.writeValueAsString(result), false);
        } catch (ToolInputException | ApiException e) {
            log.info("Разговор #{}: инструмент {} вернул ошибку: {}", conversationId, call.name(), e.getMessage());
            return new ToolResult(call.id(), e.getMessage(), true);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Разговор #{}: сбой инструмента {}", conversationId, call.name(), e);
            return new ToolResult(call.id(), "Внутренняя ошибка, предложи клиенту связаться с администратором", true);
        }
    }

    private Map<String, Object> clinicInfo() {
        ClinicInfo c = catalog.clinic();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.name());
        m.put("address", c.address());
        m.put("phone", c.phone());
        m.put("email", c.email());
        m.put("min_hours_before_visit", c.minLeadHours());
        m.put("booking_until", DAY.format(c.lastBookableDay()));
        m.put("free_cancel_hours_before_visit", c.patientCancelHours());
        m.put("site", siteUrl);
        return m;
    }

    private List<Map<String, Object>> services() {
        return catalog.services().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            if (s.description() != null && !s.description().isBlank()) {
                m.put("description", s.description());
            }
            m.put("price", money(s.price()));
            m.put("duration_minutes", s.durationMinutes());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> doctors() {
        return catalog.doctors().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.fullName());
            m.put("specialty", d.specialtyName());
            m.put("room", d.roomNumber());
            return m;
        }).toList();
    }

    private Map<String, Object> freeSlots(JsonNode in) {
        ServiceInfo service = service(in, true);
        long serviceId = service.id();
        Long doctorId = doctorId(in);
        LocalDate date = optionalDate(in, "date");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", service.name());
        if (date != null) {
            List<Map<String, Object>> slots = daySlots(serviceId, doctorId, date);
            m.put("date", date.toString());
            m.put("day", DAY.format(date));
            m.put("slots", slots);
            if (slots.isEmpty()) {
                m.put("note", "В этот день свободного времени нет — вызови find_free_slots без date, "
                        + "чтобы найти ближайшие дни");
            }
            return m;
        }
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> days = new ArrayList<>();
        for (DayAvailability d : catalog.availability(serviceId, doctorId, today, today.plusDays(SEARCH_DAYS))) {
            if (d.freeSlots() == 0) {
                continue;
            }
            List<Map<String, Object>> slots = daySlots(serviceId, doctorId, d.date());
            if (!slots.isEmpty()) {
                days.add(Map.of("date", d.date().toString(), "day", DAY.format(d.date()), "slots", slots));
            }
            if (days.size() == DAYS_TO_SUGGEST) {
                break;
            }
        }
        m.put("days", days);
        if (days.isEmpty()) {
            m.put("note", "Свободного времени для онлайн-записи нет — предложи заявку на обратный звонок");
        }
        return m;
    }

    /** Окна дня; для «любого врача» — по одному на время начала, чтобы не повторять одно время много раз. */
    private List<Map<String, Object>> daySlots(long serviceId, Long doctorId, LocalDate date) {
        Set<LocalDateTime> seen = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SlotDto s : catalog.slots(serviceId, doctorId, date)) {
            if (!seen.add(s.start())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start_at", ISO_MINUTES.format(s.start()));
            m.put("time", TIME.format(s.start()));
            m.put("doctor", s.doctorName());
            result.add(m);
            if (result.size() == SLOTS_PER_DAY) {
                break;
            }
        }
        return result;
    }

    private Map<String, Object> book(JsonNode in, Long conversationId) {
        ChatState chat = store.state(conversationId);
        String phone = optionalText(in, "phone");
        if (phone == null) {
            phone = chat.clientPhone();
        }
        if (phone == null) {
            throw new ToolInputException("Не указан телефон — спроси номер у клиента");
        }
        if (!in.path("consent").asBoolean(false)) {
            throw new ToolInputException("Нет согласия на обработку персональных данных — спроси клиента");
        }
        BookingRequest r = new BookingRequest(service(in, true).id(), doctorId(in),
                requiredDateTime(in, "start"), requiredText(in, "last_name", 60), requiredText(in, "first_name", 60),
                phone, cut(optionalText(in, "comment"), 500), true, null);
        OnlineBooking done = booking.book(r, chat.channel().leadSource(), chat.leadId());
        store.linkLead(conversationId, done.lead().getId());

        BookingInfo b = done.info();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("booked", true);
        m.put("when", DATE_TIME.format(b.startAt()) + "–" + TIME.format(b.endAt()));
        m.put("service", b.serviceName());
        m.put("price", money(b.price()));
        m.put("doctor", b.doctorName() + " (" + b.specialtyName() + ")");
        m.put("room", b.roomNumber());
        m.put("patient", b.patientName());
        m.put("link", siteUrl + "/booking/" + b.token());
        if (b.cancelDeadline() != null) {
            m.put("cancel_online_until", DATE_TIME.format(b.cancelDeadline()));
        }
        m.put("next", "Администратор позвонит, чтобы подтвердить запись. По ссылке — детали и отмена.");
        return m;
    }

    private Map<String, Object> createLead(JsonNode in, Long conversationId) {
        if (!in.path("consent").asBoolean(false)) {
            throw new ToolInputException("Нет согласия на обработку персональных данных — спроси клиента");
        }
        ChatState chat = store.state(conversationId);
        String phone = optionalText(in, "phone");
        if (phone == null && chat.clientPhone() == null) {
            throw new ToolInputException("Не указан телефон — спроси номер, чтобы администратор мог перезвонить");
        }
        ServiceInfo service = service(in, false);
        Long leadId = store.callbackLead(conversationId, cut(requiredText(in, "name", 100), 100), phone,
                service == null ? null : service.id(), optionalText(in, "preferred_time"),
                requiredText(in, "summary", 2000));
        return Map.of("lead_created", true, "lead_id", leadId,
                "next", "Администратор перезвонит в рабочее время");
    }

    private Map<String, Object> handoff(JsonNode in, Long conversationId) {
        String reason = cut(optionalText(in, "reason"), 300);
        store.handoff(conversationId, reason == null ? "по просьбе ИИ-менеджера" : reason);
        return Map.of("handed_off", true,
                "next", "Скажи клиенту, что администратор ответит в этом чате в рабочее время. Больше ничего не обещай.");
    }

    static String money(BigDecimal price) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(RU);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.##", symbols).format(price) + " ₸";
    }

    private ServiceInfo service(JsonNode in, boolean required) {
        String query = optionalText(in, "service");
        if (query == null) {
            if (required) {
                throw new ToolInputException("Не указана услуга (service) — название из list_services");
            }
            return null;
        }
        List<ServiceInfo> all = catalog.services();
        ServiceInfo found = match(all, ServiceInfo::name, query);
        if (found == null) {
            throw new ToolInputException("Услуга «" + query + "» не найдена или подходит несколько. Услуги клиники: "
                    + String.join("; ", all.stream().map(ServiceInfo::name).toList())
                    + ". Выбери подходящую или уточни у клиента.");
        }
        return found;
    }

    private Long doctorId(JsonNode in) {
        String query = optionalText(in, "doctor");
        if (query == null) {
            return null;
        }
        List<DoctorInfo> all = catalog.doctors();
        DoctorInfo found = match(all, DoctorInfo::fullName, query);
        if (found == null) {
            throw new ToolInputException("Врач «" + query + "» не найден или подходит несколько. Врачи: "
                    + String.join("; ", all.stream().map(DoctorInfo::fullName).toList()) + ".");
        }
        return found.id();
    }

    /**
     * Ищет по названию так, как его пишет модель: точно, по первому слову (фамилии), по вхождению,
     * по началам слов. Совпадение должно быть единственным — иначе null.
     */
    static <T> T match(List<T> items, Function<T, String> name, String query) {
        String q = normalize(query);
        List<Predicate<String>> rules = List.of(
                n -> n.equals(q),
                n -> n.split(" ")[0].equals(q.split(" ")[0]),
                n -> n.contains(q) || q.contains(n),
                n -> Arrays.stream(q.split(" ")).filter(w -> w.length() >= 3)
                        .allMatch(w -> n.contains(w.substring(0, Math.min(w.length(), 5)))));
        for (Predicate<String> rule : rules) {
            List<T> found = items.stream().filter(i -> rule.test(normalize(name.apply(i)))).toList();
            if (found.size() == 1) {
                return found.get(0);
            }
        }
        return null;
    }

    private static String normalize(String s) {
        return s.toLowerCase(RU).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String optionalText(JsonNode in, String field) {
        JsonNode n = in.path(field);
        return n.isTextual() && !n.asText().isBlank() ? n.asText().trim() : null;
    }

    private static String requiredText(JsonNode in, String field, int max) {
        String v = optionalText(in, field);
        if (v == null) {
            throw new ToolInputException("Не указан параметр " + field);
        }
        if (v.length() > max) {
            throw new ToolInputException("Слишком длинное значение " + field);
        }
        return v;
    }

    private static LocalDate optionalDate(JsonNode in, String field) {
        String v = optionalText(in, field);
        try {
            return v == null ? null : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new ToolInputException("Дата должна быть в формате YYYY-MM-DD");
        }
    }

    private static LocalDateTime requiredDateTime(JsonNode in, String field) {
        String v = requiredText(in, field, 30);
        try {
            return LocalDateTime.parse(v.length() == 16 ? v + ":00" : v);
        } catch (DateTimeParseException e) {
            throw new ToolInputException("Время должно быть в формате YYYY-MM-DDTHH:MM, как start_at из find_free_slots");
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        if (required.length > 0) {
            s.put("required", List.of(required));
        }
        return s;
    }

    private static Map<String, Object> props(Object... nameAndSpec) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < nameAndSpec.length; i += 2) {
            m.put((String) nameAndSpec[i], nameAndSpec[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }
}
