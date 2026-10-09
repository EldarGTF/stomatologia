package com.stomatologia.backend.service;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Holiday;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.InvoiceStatus;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentDto;
import com.stomatologia.backend.dto.DashboardDtos.DashboardDto;
import com.stomatologia.backend.dto.DashboardDtos.DayRevenue;
import com.stomatologia.backend.dto.DashboardDtos.DoctorLoad;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.InvoiceRepository;
import com.stomatologia.backend.repository.PaymentRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import com.stomatologia.backend.service.SlotCalculator.Interval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Показатели главного экрана. Все значения рассчитываются по данным БД на момент запроса.
 */
@Service
public class DashboardService {

    /** Длина «свободного окна» на Dashboard — типичная короткая услуга. */
    static final int WINDOW_MINUTES = 30;
    static final int REVENUE_DAYS = 14;
    static final int UPCOMING_LIMIT = 8;

    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final ScheduleRepository schedules;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final ClinicSettingsService clinic;
    private final LeadService leads;

    public DashboardService(AppointmentRepository appointments, DoctorRepository doctors,
                            ScheduleRepository schedules, PaymentRepository payments, InvoiceRepository invoices,
                            ClinicSettingsService clinic, LeadService leads) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.schedules = schedules;
        this.payments = payments;
        this.invoices = invoices;
        this.clinic = clinic;
        this.leads = leads;
    }

    @Transactional(readOnly = true)
    public DashboardDto today() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        List<Appointment> active = appointments.findActiveBetween(dayStart, dayEnd);
        int scheduled = count(active, AppointmentStatus.SCHEDULED);
        int completed = count(active, AppointmentStatus.COMPLETED);
        int noShow = count(active, AppointmentStatus.NO_SHOW);
        int cancelled = (int) appointments.countByStartAtGreaterThanEqualAndStartAtLessThanAndStatus(
                dayStart, dayEnd, AppointmentStatus.CANCELLED);
        int awaitingMark = (int) active.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED && !a.getEndAt().isAfter(now))
                .count();

        String holidayName = clinic.holiday(today).map(Holiday::getName).orElse(null);
        List<DoctorLoad> load = doctorLoad(today, active, now, holidayName != null);
        int freeWindows = load.stream().mapToInt(DoctorLoad::freeWindows).sum();

        BigDecimal revenueToday = payments.sumBetween(dayStart, dayEnd);
        BigDecimal revenueMonth = payments.sumBetween(today.withDayOfMonth(1).atStartOfDay(), dayEnd);
        BigDecimal outstanding = invoices.findByStatusIn(List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL))
                .stream().map(Invoice::dueAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AppointmentDto> upcoming = active.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED && a.getEndAt().isAfter(now))
                .sorted(Comparator.comparing(Appointment::getStartAt))
                .limit(UPCOMING_LIMIT)
                .map(AppointmentDto::from)
                .toList();

        return new DashboardDto(today, active.size(), scheduled, completed, noShow, cancelled, awaitingMark,
                freeWindows,
                WINDOW_MINUTES, revenueToday, revenueMonth, outstanding, revenueByDay(today), load, upcoming,
                holidayName, leads.stats());
    }

    private List<DoctorLoad> doctorLoad(LocalDate date, List<Appointment> active, LocalDateTime now,
                                        boolean holiday) {
        Map<Long, Schedule> scheduleByDoctor = schedules.findAllWithDoctor().stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek().getValue())
                .collect(Collectors.toMap(s -> s.getDoctor().getId(), Function.identity()));
        List<DoctorLoad> result = new ArrayList<>();
        for (Doctor d : doctors.findAllWithDetails()) {
            if (!d.isActive()) {
                continue;
            }
            List<Appointment> own = active.stream().filter(a -> a.getDoctor().getId().equals(d.getId())).toList();
            Schedule s = scheduleByDoctor.get(d.getId());
            String room = d.getRoom() != null ? d.getRoom().getNumber() : null;
            if (s == null || holiday) {
                result.add(new DoctorLoad(d.getId(), d.getFullName(), d.getSpecialty().getName(), room,
                        false, 0, 0, own.size(), 0, 0));
                continue;
            }
            int work = (int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            List<Interval> ownBusy = own.stream().map(a -> new Interval(a.getStartAt(), a.getEndAt())).toList();
            int booked = SlotCalculator.busyMinutes(date, s.getStartTime(), s.getEndTime(), ownBusy);
            Long roomId = d.getRoom() != null ? d.getRoom().getId() : null;
            List<Interval> busy = active.stream()
                    .filter(a -> a.getDoctor().getId().equals(d.getId()) || Objects.equals(a.getRoom().getId(), roomId))
                    .map(a -> new Interval(a.getStartAt(), a.getEndAt()))
                    .toList();
            int free = roomId == null ? 0 : SlotCalculator.freeStarts(date, s.getStartTime(), s.getEndTime(),
                    WINDOW_MINUTES, WINDOW_MINUTES, busy, now).size();
            int percent = work == 0 ? 0 : Math.round(booked * 100f / work);
            result.add(new DoctorLoad(d.getId(), d.getFullName(), d.getSpecialty().getName(), room,
                    true, work, booked, own.size(), free, percent));
        }
        result.sort(Comparator.comparing(DoctorLoad::working).reversed()
                .thenComparing(Comparator.comparingInt(DoctorLoad::loadPercent).reversed()));
        return result;
    }

    private List<DayRevenue> revenueByDay(LocalDate today) {
        LocalDate first = today.minusDays(REVENUE_DAYS - 1L);
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            byDay.put(d, BigDecimal.ZERO);
        }
        for (Payment p : payments.findByPaidAtGreaterThanEqualAndPaidAtLessThan(first.atStartOfDay(),
                today.plusDays(1).atStartOfDay())) {
            byDay.merge(p.getPaidAt().toLocalDate(), p.getAmount(), BigDecimal::add);
        }
        return byDay.entrySet().stream().map(e -> new DayRevenue(e.getKey(), e.getValue())).toList();
    }

    private static int count(List<Appointment> list, AppointmentStatus status) {
        return (int) list.stream().filter(a -> a.getStatus() == status).count();
    }
}
