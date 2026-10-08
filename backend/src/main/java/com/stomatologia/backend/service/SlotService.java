package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import com.stomatologia.backend.service.SlotCalculator.Interval;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Свободные окна для записи и поиск ближайшего свободного окна.
 */
@Service
public class SlotService {

    static final int SEARCH_DAYS = 60;

    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final ScheduleRepository schedules;
    private final ServiceCatalogService catalog;
    private final int stepMinutes;

    public SlotService(AppointmentRepository appointments, DoctorRepository doctors, ScheduleRepository schedules,
                       ServiceCatalogService catalog, @Value("${app.slot-step-minutes:15}") int stepMinutes) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.schedules = schedules;
        this.catalog = catalog;
        this.stepMinutes = stepMinutes;
    }

    @Transactional(readOnly = true)
    public List<SlotDto> freeSlots(Long doctorId, Long serviceId, LocalDate date) {
        Doctor doctor = doctors.findById(doctorId).orElseThrow(() -> ApiException.notFound("Врач не найден"));
        ClinicService service = catalog.find(serviceId);
        List<Appointment> dayAppointments = appointments.findActiveBetween(date.atStartOfDay(),
                date.plusDays(1).atStartOfDay());
        return slotsFor(doctor, service, date, dayAppointments, LocalDateTime.now());
    }

    /**
     * Ближайшее свободное окно начиная с {@code from}: у конкретного врача или у любого (doctorId == null).
     */
    @Transactional(readOnly = true)
    public SlotDto nearest(Long serviceId, Long doctorId, LocalDateTime from) {
        ClinicService service = catalog.find(serviceId);
        List<Doctor> candidates = doctorId != null
                ? List.of(doctors.findById(doctorId).orElseThrow(() -> ApiException.notFound("Врач не найден")))
                : doctors.findAllWithDetails();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime notBefore = from == null || from.isBefore(now) ? now : from;

        for (int day = 0; day <= SEARCH_DAYS; day++) {
            LocalDate date = notBefore.toLocalDate().plusDays(day);
            List<Appointment> dayAppointments = appointments.findActiveBetween(date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay());
            Optional<SlotDto> best = candidates.stream()
                    .map(d -> slotsFor(d, service, date, dayAppointments, notBefore))
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0))
                    .min(Comparator.comparing(SlotDto::start));
            if (best.isPresent()) {
                return best.get();
            }
        }
        throw ApiException.notFound("Свободных окон в ближайшие " + SEARCH_DAYS + " дней не найдено");
    }

    private List<SlotDto> slotsFor(Doctor doctor, ClinicService service, LocalDate date,
                                   List<Appointment> dayAppointments, LocalDateTime notBefore) {
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
