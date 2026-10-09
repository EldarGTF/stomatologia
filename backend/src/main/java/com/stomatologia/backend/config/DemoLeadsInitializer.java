package com.stomatologia.backend.config;

import com.stomatologia.backend.common.Phones;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ChatChannel;
import com.stomatologia.backend.domain.ChatMessage;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.ConversationMode;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.MessageRole;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.ConversationRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Демонстрационные заявки: новые из Telegram и с сайта (с перепиской), переданная оператору,
 * звонок в работе, записанные и отклонённая.
 */
@Component
@Order(4)
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoLeadsInitializer implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(DemoLeadsInitializer.class);

    private final LeadRepository leads;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ClinicServiceRepository services;
    private final AppointmentRepository appointments;
    private final UserRepository users;

    public DemoLeadsInitializer(LeadRepository leads, ConversationRepository conversations,
                                ChatMessageRepository messages, ClinicServiceRepository services,
                                AppointmentRepository appointments, UserRepository users) {
        this.leads = leads;
        this.conversations = conversations;
        this.messages = messages;
        this.services = services;
        this.appointments = appointments;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (leads.count() > 0 || appointments.count() == 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        User registrar = users.findByUsername("registrar").orElse(null);
        ClinicService caries = services.findByName("Лечение кариеса").orElse(null);
        ClinicService hygiene = services.findByName("Профессиональная гигиена").orElse(null);
        ClinicService braces = services.findByName("Установка брекет-системы").orElse(null);
        ClinicService consultation = services.findByName("Консультация стоматолога").orElse(null);
        LocalDate nextDay = nextWorkday(now.toLocalDate());

        Lead aigerim = lead(LeadSource.TELEGRAM, LeadStatus.NEW, "Айгерим", "+7 701 555-12-34", caries,
                now.minusMinutes(20));
        aigerim.setPreferredStart(nextDay.atTime(11, 30));
        aigerim.setSummary("Болит нижний зуб справа при холодном, около 3 дней. Хочет попасть как можно скорее, "
                + "удобно до обеда.");
        aigerim.setConsentAt(now.minusMinutes(17));
        leads.save(aigerim);
        chat(aigerim, "512340001", ConversationMode.AI, now.minusMinutes(26),
                "Здравствуйте! Болит зуб, когда пью холодное. Сколько стоит лечение?",
                "Здравствуйте! Точно скажет врач на осмотре, но обычно это лечение кариеса — " + money(caries)
                        + ", приём около часа. Подобрать удобное время?",
                "Да, лучше завтра до обеда",
                "На " + nextDay.getDayOfMonth() + "." + String.format("%02d", nextDay.getMonthValue())
                        + " у терапевта свободно 9:00 и 11:30. Какое время удобнее?",
                "11:30",
                "Как к вам обращаться? Нажмите «Поделиться контактом», чтобы администратор мог подтвердить запись. "
                        + "Отправляя данные, вы соглашаетесь на их обработку.",
                "Айгерим [контакт: +7 701 555-12-34]",
                "Спасибо, Айгерим! Заявка на 11:30 передана администратору, он подтвердит запись в ближайшее время.");

        Lead dmitry = lead(LeadSource.WEBSITE, LeadStatus.NEW, "Дмитрий Ковалёв", "+7 777 210-33-44", hygiene,
                now.minusHours(2));
        dmitry.setPreferredText("в субботу утром");
        dmitry.setSummary("Хочет профессиональную чистку перед отпуском.");
        dmitry.setConsentAt(dmitry.getCreatedAt());
        leads.save(dmitry);

        Lead yerzhan = lead(LeadSource.TELEGRAM, LeadStatus.NEEDS_OPERATOR, "Ержан", "+7 705 900-12-12", braces,
                now.minusHours(1));
        yerzhan.setSummary("Брекеты для сына 14 лет. Спрашивает о рассрочке — бот передал вопрос оператору.");
        yerzhan.setConsentAt(now.minusMinutes(55));
        leads.save(yerzhan);
        chat(yerzhan, "512340002", ConversationMode.OPERATOR, now.minusMinutes(62),
                "Сәлеметсіз бе! Ұлыма брекет қою қанша тұрады?",
                "Сәлеметсіз бе! Брекет-жүйесін орнату (бір жақ сүйекке) — " + money(braces)
                        + ". Алдымен ортодонт дәрігердің кеңесі керек.",
                "Бөліп төлеуге бола ма?",
                "Бөліп төлеу туралы сұрағыңызды әкімшіге бердім, ол жақын арада жауап береді.");

        Lead olga = lead(LeadSource.PHONE, LeadStatus.IN_PROGRESS, "Ольга Ли", "+7 702 333-44-55", consultation,
                now.minusDays(1).withHour(16).withMinute(40));
        olga.setPreferredText("после 18:00 в будни");
        olga.setSummary("Звонила: интересует протезирование, нужна консультация ортопеда. Перезвонить, когда "
                + "появится вечернее время.");
        olga.setAssignedTo(registrar);
        leads.save(olga);

        Set<Long> bookedPatients = new HashSet<>();
        List<Appointment> upcoming = appointments.findAll(Sort.by("startAt")).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED && a.getStartAt().isAfter(now))
                .filter(a -> bookedPatients.add(a.getPatient().getId()))
                .limit(2).toList();
        LeadSource[] bookedSources = {LeadSource.WEBSITE, LeadSource.TELEGRAM};
        for (int i = 0; i < upcoming.size(); i++) {
            Appointment a = upcoming.get(i);
            LeadSource source = bookedSources[i];
            Lead booked = lead(source, LeadStatus.BOOKED, a.getPatient().getFullName(), a.getPatient().getPhone(),
                    a.getService(), now.minusDays(2 + i).withHour(10 + i).withMinute(15));
            booked.setDoctor(a.getDoctor());
            booked.setPreferredStart(a.getStartAt());
            booked.setConsentAt(booked.getCreatedAt());
            booked.setPatient(a.getPatient());
            booked.setAppointment(a);
            booked.setAssignedTo(registrar);
            leads.save(booked);
            a.setSource(source.appointmentSource());
        }

        Lead wrong = lead(LeadSource.PHONE, LeadStatus.REJECTED, "Не представился", "+7 700 000-00-01", null,
                now.minusDays(3).withHour(12).withMinute(5));
        wrong.setSummary("Спрашивал про клинику на другой улице.");
        wrong.setRejectReason("Ошибся номером, искал другую клинику");
        wrong.setAssignedTo(registrar);
        leads.save(wrong);

        log.info("Создано демонстрационных заявок: {}", leads.count());
    }

    private static Lead lead(LeadSource source, LeadStatus status, String name, String phone, ClinicService service,
                             LocalDateTime createdAt) {
        Lead l = new Lead();
        l.setSource(source);
        l.setStatus(status);
        l.setName(name);
        l.setPhone(Phones.normalize(phone));
        l.setService(service);
        l.setCreatedAt(createdAt);
        l.setUpdatedAt(createdAt);
        return l;
    }

    /** Переписка: реплики клиента и ИИ-менеджера по очереди, начиная с клиента. */
    private void chat(Lead lead, String chatId, ConversationMode mode, LocalDateTime start, String... lines) {
        Conversation c = new Conversation();
        c.setChannel(lead.getSource() == LeadSource.WHATSAPP ? ChatChannel.WHATSAPP : ChatChannel.TELEGRAM);
        c.setExternalChatId(chatId);
        c.setLead(lead);
        c.setMode(mode);
        c.setCreatedAt(start);
        LocalDateTime at = start;
        conversations.save(c);
        for (int i = 0; i < lines.length; i++) {
            ChatMessage m = new ChatMessage();
            m.setConversation(c);
            m.setRole(i % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT);
            m.setText(lines[i]);
            m.setSentAt(at);
            messages.save(m);
            at = at.plusSeconds(i % 2 == 0 ? 4 : 40);
        }
        c.setLastMessageAt(at);
    }

    private static LocalDate nextWorkday(LocalDate today) {
        LocalDate d = today.plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static String money(ClinicService s) {
        if (s == null) {
            return "—";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols).format(s.getPrice().setScale(0, RoundingMode.HALF_UP)) + " ₸";
    }
}
