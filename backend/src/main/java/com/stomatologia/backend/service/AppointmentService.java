package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentAudit;
import com.stomatologia.backend.domain.AppointmentSource;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.AuditAction;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentDto;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.dto.AppointmentDtos.AuditDto;
import com.stomatologia.backend.repository.AppointmentAuditRepository;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
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
import java.util.List;
import java.util.Objects;

/**
 * Запись на приём: создание, перенос, отмена, смена статуса. Все изменения пишутся в журнал аудита.
 */
@Service
public class AppointmentService {

    private static final Logger log = LogManager.getLogger(AppointmentService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointments;
    private final AppointmentAuditRepository audit;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final ScheduleRepository schedules;
    private final UserRepository users;
    private final ServiceCatalogService catalog;
    private final InvoiceService invoices;
    private final ClinicSettingsService clinic;

    public AppointmentService(AppointmentRepository appointments, AppointmentAuditRepository audit,
                              DoctorRepository doctors, PatientRepository patients, ScheduleRepository schedules,
                              UserRepository users, ServiceCatalogService catalog, InvoiceService invoices,
                              ClinicSettingsService clinic) {
        this.appointments = appointments;
        this.audit = audit;
        this.doctors = doctors;
        this.patients = patients;
        this.schedules = schedules;
        this.users = users;
        this.catalog = catalog;
        this.invoices = invoices;
        this.clinic = clinic;
    }

    public record Filter(LocalDate from, LocalDate to, Long doctorId, Long patientId, AppointmentStatus status) {
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> search(Filter filter) {
        AuthUser me = CurrentUser.get();
        Long doctorId = me.is(Role.DOCTOR) ? me.doctorId() : filter.doctorId();
        Long patientId = me.is(Role.PATIENT) ? me.patientId() : filter.patientId();

        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filter.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("startAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                p.add(cb.lessThan(root.get("startAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (doctorId != null) {
                p.add(cb.equal(root.get("doctor").get("id"), doctorId));
            }
            if (patientId != null) {
                p.add(cb.equal(root.get("patient").get("id"), patientId));
            }
            if (filter.status() != null) {
                p.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return appointments.findAll(spec, Sort.by("startAt")).stream().map(AppointmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentDto get(Long id) {
        Appointment a = find(id);
        checkCanView(a, CurrentUser.get());
        return AppointmentDto.from(a);
    }

    @Transactional
    public AppointmentDto create(AppointmentRequest r) {
        return create(r, CurrentUser.get().is(Role.PATIENT) ? AppointmentSource.PATIENT_ACCOUNT
                : AppointmentSource.REGISTRY);
    }

    @Transactional
    public AppointmentDto create(AppointmentRequest r, AppointmentSource source) {
        AuthUser me = CurrentUser.get();
        if (me.is(Role.PATIENT) && r.patientId() != null && !r.patientId().equals(me.patientId())) {
            throw ApiException.forbidden("Пациент может записаться только сам");
        }
        Long patientId = me.is(Role.PATIENT) ? me.patientId() : r.patientId();
        if (patientId == null) {
            throw ApiException.badRequest("Выберите пациента");
        }
        Patient patient = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("Пациент не найден"));
        return AppointmentDto.from(book(patient, r, source, me));
    }

    /**
     * Запись с сайта: без входа в систему и с теми же ограничениями, что у пациента в личном кабинете
     * (не позже чем за min_lead_hours до приёма).
     */
    @Transactional
    public Appointment createOnline(Patient patient, AppointmentRequest r) {
        return book(patient, r, AppointmentSource.WEBSITE, null);
    }

    /** me == null — запись оформил сам клиент на сайте. */
    private Appointment book(Patient patient, AppointmentRequest r, AppointmentSource source, AuthUser me) {
        Doctor doctor = findDoctor(r.doctorId());
        ClinicService service = findActiveService(r.serviceId());

        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setRoom(doctor.getRoom());
        a.setService(service);
        a.setStartAt(r.startAt());
        a.setEndAt(r.startAt().plusMinutes(service.getDurationMinutes()));
        a.setNotes(trimToNull(r.notes()));
        a.setSource(source);
        a.setCreatedBy(me == null ? null : users.getReferenceById(me.id()));
        ensureBookable(a, null, selfService(me));

        appointments.saveAndFlush(a);
        writeAudit(a, AuditAction.CREATE, null, describe(a), me);
        log.info("Запись #{} создана: пациент {}, {}, источник «{}» (оформил {})", a.getId(), patient.getFullName(),
                describe(a), source.title(), me == null ? "клиент на сайте" : me.username());
        if (clinic.current().requiresPrepayment(service.getPrice())) {
            invoices.onBookedWithPrepayment(a);
        }
        return a;
    }

    /**
     * Изменение записи: перенос на другое время/к другому врачу, смена услуги или комментария.
     */
    @Transactional
    public AppointmentDto update(Long id, AppointmentRequest r) {
        AuthUser me = CurrentUser.get();
        Appointment a = find(id);
        checkCanModify(a, me);
        requireScheduled(a, "изменить");

        String before = describe(a);
        boolean serviceChanged = !a.getService().getId().equals(r.serviceId());
        boolean timeChanged = serviceChanged || !a.getStartAt().equals(r.startAt())
                || !a.getDoctor().getId().equals(r.doctorId());
        if (timeChanged) {
            checkPatientDeadline(a, me, "Перенести");
        }

        Doctor doctor = findDoctor(r.doctorId());
        ClinicService service = a.getService().getId().equals(r.serviceId()) ? a.getService()
                : findActiveService(r.serviceId());
        if (timeChanged) {
            // Проверяем отдельный объект: изменённая управляемая сущность была бы записана в БД до проверки.
            Appointment candidate = new Appointment();
            candidate.setPatient(a.getPatient());
            candidate.setDoctor(doctor);
            candidate.setRoom(doctor.getRoom());
            candidate.setService(service);
            candidate.setStartAt(r.startAt());
            candidate.setEndAt(r.startAt().plusMinutes(service.getDurationMinutes()));
            ensureBookable(candidate, a.getId());
        }
        a.setDoctor(doctor);
        a.setRoom(doctor.getRoom());
        a.setService(service);
        a.setStartAt(r.startAt());
        a.setEndAt(r.startAt().plusMinutes(service.getDurationMinutes()));
        a.setNotes(trimToNull(r.notes()));
        appointments.saveAndFlush(a);
        if (serviceChanged) {
            invoices.onServiceChanged(a);
        }

        String after = describe(a);
        if (!before.equals(after) || timeChanged) {
            writeAudit(a, timeChanged ? AuditAction.RESCHEDULE : AuditAction.UPDATE, before, after, me);
            log.info("Запись #{} {}: {} -> {} (изменил {})", a.getId(), timeChanged ? "перенесена" : "изменена",
                    before, after, me.username());
        }
        return AppointmentDto.from(a);
    }

    /**
     * Отмена записи. Отменённый приём не учитывается при проверке занятости, поэтому слот освобождается.
     */
    @Transactional
    public AppointmentDto cancel(Long id, String reason) {
        AuthUser me = CurrentUser.get();
        Appointment a = find(id);
        checkCanModify(a, me);
        requireScheduled(a, "отменить");
        checkPatientDeadline(a, me, "Отменить");
        return AppointmentDto.from(doCancel(a, reason, me));
    }

    /** Отмена клиентом по ссылке из онлайн-записи — с тем же сроком отмены, что в личном кабинете. */
    @Transactional
    public Appointment cancelOnline(Long id, String reason) {
        Appointment a = find(id);
        requireScheduled(a, "отменить");
        checkSelfServiceDeadline(a, "Отменить");
        return doCancel(a, reason, null);
    }

    private Appointment doCancel(Appointment a, String reason, AuthUser me) {
        a.setStatus(AppointmentStatus.CANCELLED);
        appointments.saveAndFlush(a);
        invoices.onClosedWithoutVisit(a);
        String why = trimToNull(reason);
        writeAudit(a, AuditAction.CANCEL, AppointmentStatus.SCHEDULED.title(),
                AppointmentStatus.CANCELLED.title() + (why != null ? ". Причина: " + why : ""), me);
        log.info("Запись #{} отменена, время освобождено: {} (отменил {}, причина: {})", a.getId(), describe(a),
                me == null ? "клиент на сайте" : me.username(), why != null ? why : "не указана");
        return a;
    }

    @Transactional
    public AppointmentDto changeStatus(Long id, AppointmentStatus status) {
        AuthUser me = CurrentUser.get();
        Appointment a = find(id);
        if (me.is(Role.PATIENT) || (me.is(Role.DOCTOR) && !a.getDoctor().getId().equals(me.doctorId()))) {
            throw ApiException.forbidden("Недостаточно прав для изменения статуса приёма");
        }
        if (status != AppointmentStatus.COMPLETED && status != AppointmentStatus.NO_SHOW) {
            throw ApiException.badRequest("Можно отметить только «Завершён» или «Неявка». Для отмены используйте отмену записи.");
        }
        requireScheduled(a, "отметить");
        if (a.getStartAt().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Приём ещё не начался — отметить его пока нельзя");
        }
        AppointmentStatus old = a.getStatus();
        a.setStatus(status);
        appointments.saveAndFlush(a);
        if (status == AppointmentStatus.COMPLETED) {
            invoices.onCompleted(a);
        } else {
            invoices.onClosedWithoutVisit(a);
        }
        writeAudit(a, AuditAction.STATUS, old.title(), status.title(), me);
        log.info("Запись #{}: статус «{}» -> «{}» (отметил {})", a.getId(), old.title(), status.title(), me.username());
        return AppointmentDto.from(a);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> history(Long id) {
        Appointment a = find(id);
        checkCanView(a, CurrentUser.get());
        return audit.findByAppointmentIdOrderByChangedAtDescIdDesc(id).stream().map(AuditDto::from).toList();
    }

    /**
     * Проверки перед записью: время в будущем и в пределах горизонта записи, день рабочий для клиники
     * и для врача, время в графике, врач, кабинет и пациент свободны.
     */
    void ensureBookable(Appointment a, Long excludeId) {
        ensureBookable(a, excludeId, selfService(CurrentUser.get()));
    }

    /** selfService — клиент записывается сам (личный кабинет или сайт), действует минимальное время до приёма. */
    void ensureBookable(Appointment a, Long excludeId, boolean selfService) {
        LocalDateTime start = a.getStartAt();
        LocalDateTime end = a.getEndAt();
        LocalDateTime now = LocalDateTime.now();
        if (start.isBefore(now)) {
            throw ApiException.badRequest("Нельзя записать на прошедшее время");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw ApiException.badRequest("Приём должен заканчиваться в тот же день");
        }
        ClinicSettings settings = clinic.current();
        LocalDate lastDay = settings.lastBookableDay(now.toLocalDate());
        if (start.toLocalDate().isAfter(lastDay)) {
            throw ApiException.badRequest("Запись открыта на " + settings.getBookingHorizonDays()
                    + " дн. вперёд — не позднее " + DATE.format(lastDay));
        }
        clinic.holiday(start.toLocalDate()).ifPresent(h -> {
            throw ApiException.badRequest(DATE.format(h.getDay()) + " — нерабочий день клиники («" + h.getName() + "»)");
        });
        if (selfService && start.isBefore(now.plusHours(settings.getMinLeadHours()))) {
            throw ApiException.badRequest("Онлайн-запись — не позднее чем за " + settings.getMinLeadHours()
                    + " ч до приёма. На более раннее время запишитесь по телефону " + settings.getPhone());
        }
        Doctor doctor = a.getDoctor();
        Schedule schedule = schedules.findByDoctorIdAndDayOfWeek(doctor.getId(), start.getDayOfWeek().getValue())
                .orElseThrow(() -> ApiException.badRequest("У врача " + doctor.getFullName() + " выходной в этот день"));
        if (start.toLocalTime().isBefore(schedule.getStartTime()) || end.toLocalTime().isAfter(schedule.getEndTime())) {
            throw ApiException.badRequest("Время вне графика работы врача: " + TIME.format(schedule.getStartTime())
                    + "–" + TIME.format(schedule.getEndTime()));
        }
        appointments.findDoctorOverlaps(doctor.getId(), start, end, excludeId).stream().findFirst().ifPresent(o -> {
            throw ApiException.conflict("Врач " + doctor.getFullName() + " уже занят с "
                    + TIME.format(o.getStartAt()) + " до " + TIME.format(o.getEndAt()) + ". Выберите другое время.");
        });
        appointments.findRoomOverlaps(a.getRoom().getId(), start, end, excludeId).stream().findFirst().ifPresent(o -> {
            throw ApiException.conflict("Кабинет № " + a.getRoom().getNumber() + " занят с "
                    + TIME.format(o.getStartAt()) + " до " + TIME.format(o.getEndAt()) + ". Выберите другое время.");
        });
        appointments.findPatientOverlaps(a.getPatient().getId(), start, end, excludeId).stream().findFirst()
                .ifPresent(o -> {
                    throw ApiException.conflict("У пациента уже есть запись на это время ("
                            + TIME.format(o.getStartAt()) + "–" + TIME.format(o.getEndAt()) + ", врач "
                            + o.getDoctor().getFullName() + ")");
                });
    }

    Appointment find(Long id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("Запись на приём не найдена"));
    }

    private Doctor findDoctor(Long id) {
        Doctor doctor = doctors.findById(id).orElseThrow(() -> ApiException.notFound("Врач не найден"));
        if (!doctor.isActive()) {
            throw ApiException.badRequest("Врач " + doctor.getFullName() + " сейчас не ведёт приём");
        }
        if (doctor.getRoom() == null) {
            throw ApiException.badRequest("Врачу " + doctor.getFullName() + " не назначен кабинет");
        }
        return doctor;
    }

    private ClinicService findActiveService(Long id) {
        ClinicService service = catalog.find(id);
        if (!service.isActive()) {
            throw ApiException.badRequest("Услуга «" + service.getName() + "» недоступна для записи");
        }
        return service;
    }

    private static void requireScheduled(Appointment a, String verb) {
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw ApiException.conflict("Приём в статусе «" + a.getStatus().title() + "» — " + verb + " его нельзя");
        }
    }

    static void checkCanView(Appointment a, AuthUser me) {
        if ((me.is(Role.DOCTOR) && !a.getDoctor().getId().equals(me.doctorId()))
                || (me.is(Role.PATIENT) && !a.getPatient().getId().equals(me.patientId()))) {
            throw ApiException.forbidden("Нет доступа к этой записи");
        }
    }

    private static void checkCanModify(Appointment a, AuthUser me) {
        if (me.is(Role.DOCTOR) || (me.is(Role.PATIENT) && !Objects.equals(a.getPatient().getId(), me.patientId()))) {
            throw ApiException.forbidden("Недостаточно прав для изменения записи");
        }
    }

    /** Пациент может отменить или перенести запись сам не позднее чем за patient_cancel_hours до приёма. */
    private void checkPatientDeadline(Appointment a, AuthUser me, String verb) {
        if (me.is(Role.PATIENT)) {
            checkSelfServiceDeadline(a, verb);
        }
    }

    private void checkSelfServiceDeadline(Appointment a, String verb) {
        ClinicSettings settings = clinic.current();
        if (a.getStartAt().isBefore(LocalDateTime.now().plusHours(settings.getPatientCancelHours()))) {
            throw ApiException.conflict(verb + " запись онлайн можно не позднее чем за "
                    + settings.getPatientCancelHours() + " ч до приёма. Позвоните в клинику: " + settings.getPhone());
        }
    }

    private static boolean selfService(AuthUser me) {
        return me == null || me.is(Role.PATIENT);
    }

    private void writeAudit(Appointment a, AuditAction action, String oldValue, String newValue, AuthUser me) {
        AppointmentAudit entry = new AppointmentAudit();
        entry.setAppointment(a);
        entry.setAction(action);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setChangedBy(me == null ? null : users.getReferenceById(me.id()));
        audit.save(entry);
    }

    static String describe(Appointment a) {
        return DATE_TIME.format(a.getStartAt()) + "–" + TIME.format(a.getEndAt())
                + ", врач " + a.getDoctor().getFullName()
                + ", услуга «" + a.getService().getName() + "»"
                + ", каб. " + a.getRoom().getNumber()
                + (a.getNotes() != null ? ", комментарий: " + a.getNotes() : "");
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
