package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import com.stomatologia.backend.security.CurrentUser;
import com.stomatologia.backend.service.SlotCalculator.Interval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Свободные окна для записи и поиск ближайшего свободного окна с учётом настроек клиники:
 * шаг записи, горизонт записи, нерабочие дни и минимальное время до приёма для пациентов.
 */
@Service
public class SlotService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final ScheduleRepository schedules;
    private final ServiceCatalogService catalog;
    private final ClinicSettingsService clinic;

    public SlotService(AppointmentRepository appointments, DoctorRepository doctors, ScheduleRepository schedules,
                       ServiceCatalogService catalog, ClinicSettingsService clinic) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.schedules = schedules;
        this.catalog = catalog;
        this.clinic = clinic;
    }

    /** Число свободных окон в день — для календаря онлайн-записи. */
    public record DayAvailability(LocalDate date, int freeSlots) {
    }

    static final int MAX_AVAILABILITY_DAYS = 31;

    @Transactional(readOnly = true)
    public List<SlotDto> freeSlots(Long doctorId, Long serviceId, LocalDate date) {
        return freeSlots(doctorId, serviceId, date, isPatient());
    }

    /**
     * Свободные окна на день у врача или у всех врачей (doctorId == null), по времени.
     * selfService — клиент записывается сам, окна раньше min_lead_hours не показываются.
     */
    @Transactional(readOnly = true)
    public List<SlotDto> freeSlots(Long doctorId, Long serviceId, LocalDate date, boolean selfService) {
        List<Doctor> candidates = candidates(doctorId);
        ClinicService service = catalog.find(serviceId);
        ClinicSettings settings = clinic.current();
        LocalDateTime now = LocalDateTime.now();
        if (date.isAfter(settings.lastBookableDay(now.toLocalDate())) || clinic.holiday(date).isPresent()) {
            return List.of();
        }
        List<Appointment> dayAppointments = appointments.findActiveBetween(date.atStartOfDay(),
                date.plusDays(1).atStartOfDay());
        LocalDateTime notBefore = earliestStart(settings, now, selfService);
        return candidates.stream()
                .flatMap(d -> slotsFor(d, service, date, dayAppointments, notBefore,
                        settings.getSlotStepMinutes()).stream())
                .sorted(Comparator.comparing(SlotDto::start).thenComparing(SlotDto::doctorName))
                .toList();
    }

    /** Сколько свободных окон в каждый день периода (не длиннее месяца), с учётом горизонта и праздников. */
    @Transactional(readOnly = true)
    public List<DayAvailability> availability(Long serviceId, Long doctorId, LocalDate from, LocalDate to,
                                              boolean selfService) {
        if (to.isBefore(from) || from.plusDays(MAX_AVAILABILITY_DAYS).isBefore(to)) {
            throw ApiException.badRequest("Период — не больше " + MAX_AVAILABILITY_DAYS + " дней");
        }
        List<Doctor> candidates = candidates(doctorId);
        ClinicService service = catalog.find(serviceId);
        ClinicSettings settings = clinic.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDate first = from.isBefore(now.toLocalDate()) ? now.toLocalDate() : from;
        LocalDate last = to.isAfter(settings.lastBookableDay(now.toLocalDate()))
                ? settings.lastBookableDay(now.toLocalDate()) : to;
        if (last.isBefore(first)) {
            return List.of();
        }
        Set<LocalDate> holidays = clinic.holidayDates(first, last);
        List<Appointment> busy = appointments.findActiveBetween(first.atStartOfDay(), last.plusDays(1).atStartOfDay());
        LocalDateTime notBefore = earliestStart(settings, now, selfService);
        List<DayAvailability> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (holidays.contains(day)) {
                continue;
            }
            LocalDate date = day;
            int count = candidates.stream()
                    .mapToInt(d -> slotsFor(d, service, date, busy, notBefore, settings.getSlotStepMinutes()).size())
                    .sum();
            if (count > 0) {
                days.add(new DayAvailability(date, count));
            }
        }
        return days;
    }

    /**
     * Ближайшее свободное окно начиная с {@code from}: у конкретного врача или у любого (doctorId == null).
     */
    @Transactional(readOnly = true)
    public SlotDto nearest(Long serviceId, Long doctorId, LocalDateTime from) {
        return nearest(serviceId, doctorId, from, isPatient());
    }

    @Transactional(readOnly = true)
    public SlotDto nearest(Long serviceId, Long doctorId, LocalDateTime from, boolean selfService) {
        ClinicService service = catalog.find(serviceId);
        List<Doctor> candidates = candidates(doctorId);
        ClinicSettings settings = clinic.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = earliestStart(settings, now, selfService);
        LocalDateTime notBefore = from == null || from.isBefore(earliest) ? earliest : from;
        LocalDate lastDay = settings.lastBookableDay(now.toLocalDate());
        Set<LocalDate> holidays = clinic.holidayDates(notBefore.toLocalDate(), lastDay);

        for (LocalDate date = notBefore.toLocalDate(); !date.isAfter(lastDay); date = date.plusDays(1)) {
            if (holidays.contains(date)) {
                continue;
            }
            LocalDate day = date;
            List<Appointment> dayAppointments = appointments.findActiveBetween(day.atStartOfDay(),
                    day.plusDays(1).atStartOfDay());
            Optional<SlotDto> best = candidates.stream()
                    .map(d -> slotsFor(d, service, day, dayAppointments, notBefore, settings.getSlotStepMinutes()))
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0))
                    .min(Comparator.comparing(SlotDto::start));
            if (best.isPresent()) {
                return best.get();
            }
        }
        throw ApiException.notFound("Свободных окон до " + DATE.format(lastDay) + " не найдено (запись открыта на "
                + settings.getBookingHorizonDays() + " дн. вперёд)");
    }

    /** Клиент записывается сам не раньше чем за min_lead_hours до приёма; персонал — на любое будущее время. */
    private static LocalDateTime earliestStart(ClinicSettings settings, LocalDateTime now, boolean selfService) {
        return selfService ? now.plusHours(settings.getMinLeadHours()) : now;
    }

    private static boolean isPatient() {
        return CurrentUser.get().is(Role.PATIENT);
    }

    private List<Doctor> candidates(Long doctorId) {
        return doctorId != null
                ? List.of(doctors.findById(doctorId).orElseThrow(() -> ApiException.notFound("Врач не найден")))
                : doctors.findAllWithDetails();
    }

    private List<SlotDto> slotsFor(Doctor doctor, ClinicService service, LocalDate date,
                                   List<Appointment> dayAppointments, LocalDateTime notBefore, int stepMinutes) {
        if (!doctor.isActive() || doctor.getRoom() == null) {
            return List.of();
        }
        Optional<Schedule> schedule = schedules.findByDoctorIdAndDayOfWeek(doctor.getId(),
                date.getDayOfWeek().getValue());
        if (schedule.isEmpty()) {
            return List.of();
        }
        Long roomId = doctor.getRoom().getId();
        List<Interval> busy = dayAppointments.stream()
                .filter(a -> a.getDoctor().getId().equals(doctor.getId())
                        || Objects.equals(a.getRoom().getId(), roomId))
                .map(a -> new Interval(a.getStartAt(), a.getEndAt()))
                .toList();
        int duration = service.getDurationMinutes();
        return SlotCalculator.freeStarts(date, schedule.get().getStartTime(), schedule.get().getEndTime(),
                        duration, stepMinutes, busy, notBefore).stream()
                .map(start -> new SlotDto(doctor.getId(), doctor.getFullName(), doctor.getRoom().getNumber(),
                        start, start.plusMinutes(duration)))
                .toList();
    }
}
