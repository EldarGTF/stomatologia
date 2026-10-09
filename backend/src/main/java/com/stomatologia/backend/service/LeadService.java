package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.common.Phones;
import com.stomatologia.backend.domain.Conversation;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentDto;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.dto.LeadDtos.ChatMessageDto;
import com.stomatologia.backend.dto.LeadDtos.LeadBookRequest;
import com.stomatologia.backend.dto.LeadDtos.LeadDto;
import com.stomatologia.backend.dto.LeadDtos.LeadRequest;
import com.stomatologia.backend.dto.LeadDtos.LeadStats;
import com.stomatologia.backend.dto.PatientDtos.PatientDto;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.ConversationRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import com.stomatologia.backend.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Заявки: список, взятие в работу, отказ и запись на приём. Запись создаётся через {@link AppointmentService},
 * поэтому действуют все проверки расписания; новый пациент создаётся в той же транзакции.
 */
@Service
public class LeadService {

    private static final Logger log = LogManager.getLogger(LeadService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    static final int CONVERSION_DAYS = 30;

    private final LeadRepository leads;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final PatientRepository patients;
    private final ClinicServiceRepository services;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final AppointmentService appointmentService;

    public LeadService(LeadRepository leads, ConversationRepository conversations, ChatMessageRepository messages,
                       PatientRepository patients, ClinicServiceRepository services, DoctorRepository doctors,
                       AppointmentRepository appointments, UserRepository users,
                       AppointmentService appointmentService) {
        this.leads = leads;
        this.conversations = conversations;
        this.messages = messages;
        this.patients = patients;
        this.services = services;
        this.doctors = doctors;
        this.appointments = appointments;
        this.users = users;
        this.appointmentService = appointmentService;
    }

    /** openOnly — только заявки, ждущие действий; status уточняет конкретный статус. */
    public record Filter(boolean openOnly, LeadStatus status, LeadSource source, LocalDate from, LocalDate to,
                         String query) {
    }

    @Transactional(readOnly = true)
    public List<LeadDto> search(Filter f) {
        Specification<Lead> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            } else if (f.openOnly()) {
                p.add(cb.or(root.get("status").in(LeadStatus.OPEN),
                        cb.and(cb.equal(root.get("status"), LeadStatus.BOOKED), cb.isNull(root.get("confirmedAt")))));
            }
            if (f.source() != null) {
                p.add(cb.equal(root.get("source"), f.source()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay()));
            }
            if (f.query() != null && !f.query().isBlank()) {
                String q = "%" + f.query().trim().toLowerCase() + "%";
                String digits = f.query().replaceAll("\\D", "");
                Predicate byName = cb.like(cb.lower(root.get("name")), q);
                p.add(digits.length() >= 3
                        ? cb.or(byName, cb.like(cb.function("regexp_replace", String.class,
                        cb.coalesce(root.get("phone"), ""), cb.literal("[^0-9]"), cb.literal(""), cb.literal("g")),
                        "%" + digits + "%"))
                        : byName);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        List<Lead> list = leads.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, Conversation> chats = new HashMap<>();
        if (!list.isEmpty()) {
            for (Conversation c : conversations.findByLeadIds(list.stream().map(Lead::getId).toList())) {
                chats.merge(c.getLead().getId(), c,
                        (a, b) -> a.getLastMessageAt().isAfter(b.getLastMessageAt()) ? a : b);
            }
        }
        return list.stream().map(l -> LeadDto.from(l, chats.get(l.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public LeadDto get(Long id) {
        return dto(find(id));
    }

    @Transactional
    public LeadDto create(LeadRequest r) {
        AuthUser me = CurrentUser.get();
        Lead l = new Lead();
        apply(l, r);
        l.setSource(r.source());
        l.setStatus(LeadStatus.IN_PROGRESS);
        l.setAssignedTo(users.getReferenceById(me.id()));
        leads.saveAndFlush(l);
        log.info("Заявка #{} создана вручную: {} {}, источник «{}» ({})", l.getId(), l.getName(),
                nullToDash(l.getPhone()), l.getSource().title(), me.username());
        return dto(l);
    }

    @Transactional
    public LeadDto update(Long id, LeadRequest r) {
        Lead l = find(id);
        requireOpen(l, "изменить");
        apply(l, r);
        leads.saveAndFlush(l);
        log.info("Заявка #{} изменена ({})", l.getId(), CurrentUser.get().username());
        return dto(l);
    }

    @Transactional
    public LeadDto take(Long id) {
        AuthUser me = CurrentUser.get();
        Lead l = find(id);
        requireOpen(l, "взять в работу");
        LeadStatus old = l.getStatus();
        l.setStatus(LeadStatus.IN_PROGRESS);
        l.setAssignedTo(users.getReferenceById(me.id()));
        leads.saveAndFlush(l);
        log.info("Заявка #{} взята в работу: «{}» -> «{}» ({})", l.getId(), old.title(),
                LeadStatus.IN_PROGRESS.title(), me.username());
        return dto(l);
    }

    @Transactional
    public LeadDto reject(Long id, String reason) {
        AuthUser me = CurrentUser.get();
        Lead l = find(id);
        requireOpen(l, "отклонить");
        l.setStatus(LeadStatus.REJECTED);
        l.setRejectReason(reason.trim());
        if (l.getAssignedTo() == null) {
            l.setAssignedTo(users.getReferenceById(me.id()));
        }
        leads.saveAndFlush(l);
        log.info("Заявка #{} отклонена: {} ({})", l.getId(), l.getRejectReason(), me.username());
        return dto(l);
    }

    @Transactional
    public LeadDto reopen(Long id) {
        AuthUser me = CurrentUser.get();
        Lead l = find(id);
        if (l.getStatus() != LeadStatus.REJECTED) {
            throw ApiException.conflict("Вернуть в работу можно только отклонённую заявку");
        }
        l.setStatus(LeadStatus.IN_PROGRESS);
        l.setRejectReason(null);
        l.setAssignedTo(users.getReferenceById(me.id()));
        leads.saveAndFlush(l);
        log.info("Заявка #{} возвращена в работу ({})", l.getId(), me.username());
        return dto(l);
    }

    /**
     * Запись по заявке. Ошибка записи (врач занят, нерабочий день) откатывает и создание пациента.
     */
    @Transactional
    public LeadDto book(Long id, LeadBookRequest r) {
        AuthUser me = CurrentUser.get();
        Lead l = find(id);
        requireOpen(l, "записать");
        Patient patient = r.patientId() != null ? existingPatient(r.patientId(), r.phone()) : newPatient(r);

        AppointmentDto a = appointmentService.create(
                new AppointmentRequest(patient.getId(), r.doctorId(), r.serviceId(), r.startAt(), r.notes()),
                l.getSource().appointmentSource());

        l.setPatient(patient);
        l.setAppointment(appointments.getReferenceById(a.id()));
        l.setStatus(LeadStatus.BOOKED);
        l.setConfirmedAt(LocalDateTime.now());
        if (l.getAssignedTo() == null) {
            l.setAssignedTo(users.getReferenceById(me.id()));
        }
        leads.saveAndFlush(l);
        log.info("Заявка #{} -> запись #{}: пациент {}{}, {} у врача {} ({})", l.getId(), a.id(),
                patient.getFullName(), r.patientId() == null ? " (новый)" : "", DATE_TIME.format(a.startAt()),
                a.doctorName(), me.username());
        return dto(l);
    }

    /** Регистратор связался с клиентом и подтвердил онлайн-запись. */
    @Transactional
    public LeadDto confirm(Long id) {
        AuthUser me = CurrentUser.get();
        Lead l = find(id);
        if (!l.awaitsConfirmation()) {
            throw ApiException.conflict("Подтвердить можно только онлайн-запись, которая ещё не подтверждена");
        }
        l.setConfirmedAt(LocalDateTime.now());
        if (l.getAssignedTo() == null) {
            l.setAssignedTo(users.getReferenceById(me.id()));
        }
        leads.saveAndFlush(l);
        log.info("Заявка #{}: онлайн-запись #{} подтверждена ({})", l.getId(),
                l.getAppointment() != null ? l.getAppointment().getId() : null, me.username());
        return dto(l);
    }

    /** Пациенты с тем же телефоном, что в заявке, — чтобы не завести дубль. */
    @Transactional(readOnly = true)
    public List<PatientDto> matchingPatients(Long id) {
        String digits = Phones.lastTenDigits(find(id).getPhone());
        return digits == null ? List.of() : patients.findByPhoneDigits(digits).stream().map(PatientDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> messages(Long id) {
        find(id);
        return messages.findByLeadId(id).stream().map(ChatMessageDto::from).toList();
    }

    @Transactional(readOnly = true)
    public LeadStats stats() {
        LocalDate today = LocalDate.now();
        LocalDateTime periodStart = today.minusDays(CONVERSION_DAYS - 1L).atStartOfDay();
        long created = leads.countByCreatedAtGreaterThanEqual(periodStart);
        long booked = leads.countByCreatedAtGreaterThanEqualAndStatus(periodStart, LeadStatus.BOOKED);
        return new LeadStats((int) leads.countByCreatedAtGreaterThanEqual(today.atStartOfDay()),
                (int) (leads.countByStatusIn(LeadStatus.OPEN)
                        + leads.countByStatusAndConfirmedAtIsNull(LeadStatus.BOOKED)),
                created == 0 ? 0 : Math.round(booked * 100f / created));
    }

    private Patient existingPatient(Long patientId, String phone) {
        Patient p = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("Пациент не найден"));
        String normalized = Phones.normalize(phone);
        if ((p.getPhone() == null || p.getPhone().isBlank()) && normalized != null) {
            p.setPhone(normalized);
        }
        return p;
    }

    private Patient newPatient(LeadBookRequest r) {
        if (r.lastName() == null || r.lastName().isBlank() || r.firstName() == null || r.firstName().isBlank()) {
            throw ApiException.badRequest("Для нового пациента укажите фамилию и имя");
        }
        Patient p = new Patient();
        p.setLastName(r.lastName().trim());
        p.setFirstName(r.firstName().trim());
        p.setPhone(Phones.normalize(r.phone()));
        return patients.saveAndFlush(p);
    }

    private void apply(Lead l, LeadRequest r) {
        l.setName(r.name().trim());
        l.setPhone(Phones.normalize(r.phone()));
        l.setService(r.serviceId() == null ? null : services.findById(r.serviceId())
                .orElseThrow(() -> ApiException.notFound("Услуга не найдена")));
        l.setDoctor(r.doctorId() == null ? null : doctors.findById(r.doctorId())
                .orElseThrow(() -> ApiException.notFound("Врач не найден")));
        l.setPreferredStart(r.preferredStart());
        l.setPreferredText(trimToNull(r.preferredText()));
        l.setSummary(trimToNull(r.summary()));
    }

    LeadDto dto(Lead l) {
        return LeadDto.from(l, l.getId() == null ? null
                : conversations.findFirstByLeadIdOrderByLastMessageAtDesc(l.getId()).orElse(null));
    }

    Lead find(Long id) {
        return leads.findById(id).orElseThrow(() -> ApiException.notFound("Заявка не найдена"));
    }

    private static void requireOpen(Lead l, String verb) {
        if (!l.getStatus().isOpen()) {
            throw ApiException.conflict("Заявка в статусе «" + l.getStatus().title() + "» — " + verb + " её нельзя");
        }
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nullToDash(String s) {
        return s == null ? "—" : s;
    }
}
