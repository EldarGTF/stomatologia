package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.common.Phones;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentSource;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.dto.PublicDtos.BookingInfo;
import com.stomatologia.backend.dto.PublicDtos.BookingRequest;
import com.stomatologia.backend.report.WordDocuments;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.PatientRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Онлайн-запись с сайта. Запись создаётся сразу (клиент выбирает реально свободное время), а в «Заявках»
 * появляется заявка «Записан, не подтверждена» — регистратор перезванивает и подтверждает её.
 * Клиент получает личную ссылку: по ней он видит запись, скачивает талон и может отменить запись.
 */
@Service
public class PublicBookingService {

    private static final Logger log = LogManager.getLogger(PublicBookingService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MAX_ACTIVE_ONLINE_BOOKINGS = 2;
    static final Set<AppointmentSource> ONLINE_SOURCES = EnumSet.of(AppointmentSource.WEBSITE, AppointmentSource.MESSENGER);
    static final String CANCEL_REASON = "Клиент отменил запись на сайте";

    private final AppointmentService appointmentService;
    private final SlotService slots;
    private final ClinicSettingsService clinic;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final LeadRepository leads;
    private final WordDocuments word;

    public PublicBookingService(AppointmentService appointmentService, SlotService slots, ClinicSettingsService clinic,
                                PatientRepository patients, AppointmentRepository appointments, LeadRepository leads,
                                WordDocuments word) {
        this.appointmentService = appointmentService;
        this.slots = slots;
        this.clinic = clinic;
        this.patients = patients;
        this.appointments = appointments;
        this.leads = leads;
        this.word = word;
    }

    /** Запись и заявка, по которой её видит регистратор. */
    public record OnlineBooking(Lead lead, BookingInfo info) {
    }

    @Transactional
    public BookingInfo book(BookingRequest r) {
        return book(r, LeadSource.WEBSITE, null).info();
    }

    /**
     * Онлайн-запись из любого канала: сайт, чат на сайте, мессенджер. reuseLeadId — заявка разговора:
     * если она ещё открыта, её дополняет запись вместо того, чтобы заводить вторую.
     */
    @Transactional
    public OnlineBooking book(BookingRequest r, LeadSource source, Long reuseLeadId) {
        if (r.website() != null && !r.website().isBlank()) {
            log.warn("Онлайн-запись отклонена: заполнено скрытое поле, похоже на бота");
            throw ApiException.badRequest("Не удалось отправить форму. Обновите страницу и попробуйте ещё раз");
        }
        if (!r.consent()) {
            throw ApiException.badRequest("Чтобы записаться, нужно согласие на обработку персональных данных");
        }
        if (!Phones.isValid(r.phone())) {
            throw ApiException.badRequest("Укажите номер телефона полностью, например +7 701 123 45 67");
        }
        String phone = Phones.normalize(r.phone());
        String lastName = r.lastName().trim();
        String firstName = r.firstName().trim();

        List<Patient> samePhone = patients.findByPhoneDigits(Phones.lastTenDigits(phone));
        ClinicSettings settings = clinic.current();
        if (!samePhone.isEmpty() && appointments.countUpcoming(samePhone.stream().map(Patient::getId).toList(),
                ONLINE_SOURCES, LocalDateTime.now()) >= MAX_ACTIVE_ONLINE_BOOKINGS) {
            throw ApiException.conflict("На этот номер уже есть " + MAX_ACTIVE_ONLINE_BOOKINGS
                    + " предстоящие онлайн-записи. Чтобы записаться ещё, позвоните в клинику: " + settings.getPhone());
        }

        Long doctorId = r.doctorId() != null ? r.doctorId() : anyFreeDoctor(r);
        boolean known = samePhone.stream().anyMatch(p -> sameName(p, lastName, firstName));
        Patient patient = samePhone.stream().filter(p -> sameName(p, lastName, firstName)).findFirst()
                .orElseGet(() -> newPatient(lastName, firstName, phone));
        String comment = r.comment() == null || r.comment().isBlank() ? null : r.comment().trim();
        Appointment a = appointmentService.createOnline(patient,
                new AppointmentRequest(patient.getId(), doctorId, r.serviceId(), r.startAt(), comment),
                source.appointmentSource());

        Lead l = (reuseLeadId == null ? Optional.<Lead>empty() : leads.findById(reuseLeadId))
                .filter(x -> x.getStatus().isOpen())
                .orElseGet(() -> {
                    Lead fresh = new Lead();
                    fresh.setSource(source);
                    return fresh;
                });
        l.setStatus(LeadStatus.BOOKED);
        l.setName(firstName + " " + lastName);
        l.setPhone(phone);
        l.setService(a.getService());
        l.setDoctor(a.getDoctor());
        l.setPreferredStart(a.getStartAt());
        if (comment != null) {
            String summary = l.getSummary() == null ? comment : l.getSummary() + "\n" + comment;
            l.setSummary(summary.length() > 2000 ? summary.substring(0, 2000) : summary);
        }
        l.setConsentAt(LocalDateTime.now());
        l.setPatient(patient);
        l.setAppointment(a);
        l.setConfirmedAt(null);
        l.setPublicToken(newToken());
        leads.saveAndFlush(l);
        log.info("Онлайн-запись ({}): заявка #{}, приём #{} — {} ({}), {} у врача {}", source.title(), l.getId(),
                a.getId(), patient.getFullName(), known ? "пациент найден по телефону" : "новый пациент",
                DATE_TIME.format(a.getStartAt()), a.getDoctor().getFullName());
        return new OnlineBooking(l, info(l));
    }

    @Transactional(readOnly = true)
    public BookingInfo get(String token) {
        return info(find(token));
    }

    @Transactional(readOnly = true)
    public byte[] ticket(String token) {
        Lead l = find(token);
        Appointment a = l.getAppointment();
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw ApiException.conflict("Запись в статусе «" + a.getStatus().title() + "» — талон не нужен");
        }
        log.info("Талон на приём #{} скачан по ссылке онлайн-записи", a.getId());
        return word.ticket(a, clinic.current(), "Онлайн-запись на сайте");
    }

    @Transactional
    public BookingInfo cancel(String token) {
        Lead l = find(token);
        appointmentService.cancelOnline(l.getAppointment().getId(), CANCEL_REASON);
        l.setStatus(LeadStatus.REJECTED);
        l.setRejectReason(CANCEL_REASON);
        leads.saveAndFlush(l);
        log.info("Онлайн-запись отменена клиентом: заявка #{}, приём #{}", l.getId(), l.getAppointment().getId());
        return info(l);
    }

    /** «Любой врач»: первый по алфавиту врач, у которого это время свободно. */
    private Long anyFreeDoctor(BookingRequest r) {
        return slots.freeSlots(null, r.serviceId(), r.startAt().toLocalDate(), true).stream()
                .filter(s -> s.start().equals(r.startAt()))
                .map(SlotDto::doctorId)
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("Это время уже заняли. Выберите, пожалуйста, другое"));
    }

    private Patient newPatient(String lastName, String firstName, String phone) {
        Patient p = new Patient();
        p.setLastName(lastName);
        p.setFirstName(firstName);
        p.setPhone(phone);
        return patients.saveAndFlush(p);
    }

    private Lead find(String token) {
        return leads.findByPublicToken(token)
                .filter(l -> l.getAppointment() != null)
                .orElseThrow(() -> ApiException.notFound("Запись не найдена. Проверьте ссылку или позвоните в клинику"));
    }

    private BookingInfo info(Lead l) {
        Appointment a = l.getAppointment();
        LocalDateTime deadline = a.getStartAt().minusHours(clinic.current().getPatientCancelHours());
        boolean cancellable = a.getStatus() == AppointmentStatus.SCHEDULED && LocalDateTime.now().isBefore(deadline);
        return new BookingInfo(l.getPublicToken(), a.getStatus(), a.getStatus().title(), a.getStartAt(), a.getEndAt(),
                a.getDoctor().getFullName(), a.getDoctor().getSpecialty().getName(), a.getRoom().getNumber(),
                a.getService().getName(), a.getService().getPrice(), a.getPatient().getFullName(), cancellable,
                deadline, l.getConfirmedAt() != null);
    }

    private static boolean sameName(Patient p, String lastName, String firstName) {
        return p.getLastName().equalsIgnoreCase(lastName) && p.getFirstName().equalsIgnoreCase(firstName);
    }

    static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
