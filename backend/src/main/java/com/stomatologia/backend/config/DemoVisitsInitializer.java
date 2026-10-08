package com.stomatologia.backend.config;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentAudit;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.AuditAction;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.repository.AppointmentAuditRepository;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Демонстрационные приёмы: две недели истории и неделя вперёд, чтобы Dashboard и отчёты были не пустыми.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoVisitsInitializer implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(DemoVisitsInitializer.class);
    private static final int[] OFFSETS_MINUTES = {0, 90, 180, 300, 420};

    private final AppointmentRepository appointments;
    private final AppointmentAuditRepository audit;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final ScheduleRepository schedules;
    private final ClinicServiceRepository services;
    private final UserRepository users;

    public DemoVisitsInitializer(AppointmentRepository appointments, AppointmentAuditRepository audit,
                                 DoctorRepository doctors, PatientRepository patients, ScheduleRepository schedules,
                                 ClinicServiceRepository services, UserRepository users) {
        this.appointments = appointments;
        this.audit = audit;
        this.doctors = doctors;
        this.patients = patients;
        this.schedules = schedules;
        this.services = services;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appointments.count() > 0 || schedules.count() == 0) {
            return;
        }
        int created = seed();
        log.info("Создано демонстрационных приёмов: {}", created);
    }

    private int seed() {
        Random random = new Random(42);
        List<Patient> allPatients = patients.findAll();
        List<ClinicService> shortServices = services.findByActiveTrueOrderByName().stream()
                .filter(s -> s.getDurationMinutes() <= 90)
                .toList();
        User registrar = users.findByUsername("registrar").orElse(null);
        if (allPatients.isEmpty() || shortServices.isEmpty()) {
            return 0;
        }
        Map<Long, List<Appointment>> byPatient = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        for (int dayOffset = -14; dayOffset <= 7; dayOffset++) {
            LocalDate date = LocalDate.now().plusDays(dayOffset);
            for (Doctor doctor : doctors.findAllWithDetails()) {
                if (doctor.getRoom() == null || !doctor.isActive()) {
                    continue;
                }
                Schedule schedule = schedules.findByDoctorIdAndDayOfWeek(doctor.getId(),
                        date.getDayOfWeek().getValue()).orElse(null);
                if (schedule == null) {
                    continue;
                }
                for (int offset : OFFSETS_MINUTES) {
                    if (random.nextInt(100) < 25) {
                        continue;
                    }
                    ClinicService service = shortServices.get(random.nextInt(shortServices.size()));
                    LocalDateTime start = date.atTime(schedule.getStartTime()).plusMinutes(offset);
                    LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
                    if (end.toLocalTime().isAfter(schedule.getEndTime())) {
                        continue;
                    }
                    Patient patient = freePatient(allPatients, byPatient, start, end, random);
                    if (patient == null) {
                        continue;
                    }
                    Appointment a = new Appointment();
                    a.setPatient(patient);
                    a.setDoctor(doctor);
                    a.setRoom(doctor.getRoom());
                    a.setService(service);
                    a.setStartAt(start);
                    a.setEndAt(end);
                    a.setStatus(status(end.isBefore(now), random));
                    a.setCreatedBy(registrar);
                    LocalDateTime createdAt = start.minusDays(3 + random.nextInt(5));
                    a.setCreatedAt(createdAt.isAfter(now) ? now.minusHours(1 + random.nextInt(48)) : createdAt);
                    appointments.save(a);
                    byPatient.computeIfAbsent(patient.getId(), k -> new ArrayList<>()).add(a);
                    log(a, AuditAction.CREATE, registrar);
                    if (a.getStatus() != AppointmentStatus.SCHEDULED) {
                        log(a, a.getStatus() == AppointmentStatus.CANCELLED ? AuditAction.CANCEL : AuditAction.STATUS,
                                registrar);
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private static Patient freePatient(List<Patient> all, Map<Long, List<Appointment>> byPatient,
                                       LocalDateTime start, LocalDateTime end, Random random) {
        int first = random.nextInt(all.size());
        for (int i = 0; i < all.size(); i++) {
            Patient p = all.get((first + i) % all.size());
            boolean busy = byPatient.getOrDefault(p.getId(), List.of()).stream()
                    .anyMatch(a -> a.getStartAt().isBefore(end) && a.getEndAt().isAfter(start));
            if (!busy) {
                return p;
            }
        }
        return null;
    }

    private static AppointmentStatus status(boolean past, Random random) {
        int roll = random.nextInt(100);
        if (past) {
            return roll < 80 ? AppointmentStatus.COMPLETED : roll < 90 ? AppointmentStatus.NO_SHOW
                    : AppointmentStatus.CANCELLED;
        }
        return roll < 92 ? AppointmentStatus.SCHEDULED : AppointmentStatus.CANCELLED;
    }

    private void log(Appointment a, AuditAction action, User by) {
        AppointmentAudit entry = new AppointmentAudit();
        entry.setAppointment(a);
        entry.setAction(action);
        entry.setNewValue(action == AuditAction.CREATE ? "Запись создана" : a.getStatus().title());
        entry.setOldValue(action == AuditAction.CREATE ? null : AppointmentStatus.SCHEDULED.title());
        entry.setChangedBy(by);
        entry.setChangedAt(action == AuditAction.CREATE ? a.getCreatedAt() : a.getEndAt());
        audit.save(entry);
    }
}
