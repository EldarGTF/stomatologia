package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.InvoiceStatus;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.report.ExcelReports;
import com.stomatologia.backend.report.ExcelReports.DoctorPeriodLoad;
import com.stomatologia.backend.report.WordDocuments;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.InvoiceRepository;
import com.stomatologia.backend.repository.PaymentRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import com.stomatologia.backend.security.CurrentUser;
import com.stomatologia.backend.service.SlotCalculator.Interval;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Формирование документов Word и отчётов Excel по данным БД.
 */
@Service
public class ReportService {

    static final int MAX_PERIOD_DAYS = 366;

    private final AppointmentService appointmentService;
    private final InvoiceService invoiceService;
    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final ScheduleRepository schedules;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final WordDocuments word;
    private final ExcelReports excel;

    public ReportService(AppointmentService appointmentService, InvoiceService invoiceService,
                         AppointmentRepository appointments, DoctorRepository doctors, ScheduleRepository schedules,
                         PaymentRepository payments, InvoiceRepository invoices, WordDocuments word,
                         ExcelReports excel) {
        this.appointmentService = appointmentService;
        this.invoiceService = invoiceService;
        this.appointments = appointments;
        this.doctors = doctors;
        this.schedules = schedules;
        this.payments = payments;
        this.invoices = invoices;
        this.word = word;
        this.excel = excel;
    }

    @Transactional(readOnly = true)
    public byte[] ticket(Long appointmentId) {
        Appointment a = appointmentService.find(appointmentId);
        AppointmentService.checkCanView(a, CurrentUser.get());
        return word.ticket(a, CurrentUser.get().fullName());
    }

    @Transactional(readOnly = true)
    public byte[] invoice(Long invoiceId) {
        Invoice invoice = invoiceService.find(invoiceId);
        InvoiceService.checkCanView(invoice);
        return word.invoice(invoice, CurrentUser.get().fullName());
    }

    @Transactional(readOnly = true)
    public byte[] doctorLoad(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        Specification<Appointment> inPeriod = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("startAt"), from.atStartOfDay()),
                cb.lessThan(root.get("startAt"), to.plusDays(1).atStartOfDay()));
        Map<Long, List<Appointment>> byDoctor = appointments.findAll(inPeriod, Sort.by("startAt")).stream()
                .collect(Collectors.groupingBy(a -> a.getDoctor().getId()));
        Map<Long, Map<Integer, Schedule>> week = new HashMap<>();
        for (Schedule s : schedules.findAllWithDoctor()) {
            week.computeIfAbsent(s.getDoctor().getId(), k -> new HashMap<>()).put(s.getDayOfWeek(), s);
        }

        List<DoctorPeriodLoad> rows = new ArrayList<>();
        for (Doctor d : doctors.findAllWithDetails()) {
            List<Appointment> own = byDoctor.getOrDefault(d.getId(), List.of());
            if (!d.isActive() && own.isEmpty()) {
                continue;
            }
            Map<Integer, Schedule> doctorWeek = week.getOrDefault(d.getId(), Map.of());
            Map<LocalDate, Integer> daily = new HashMap<>();
            int workDays = 0;
            int workMinutes = 0;
            int bookedMinutes = 0;
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                Schedule s = doctorWeek.get(day.getDayOfWeek().getValue());
                if (s == null) {
                    continue;
                }
                LocalDate date = day;
                List<Interval> busy = own.stream()
                        .filter(a -> a.isActive() && a.getStartAt().toLocalDate().equals(date))
                        .map(a -> new Interval(a.getStartAt(), a.getEndAt()))
                        .toList();
                int work = (int) Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
                int booked = SlotCalculator.busyMinutes(day, s.getStartTime(), s.getEndTime(), busy);
                workDays++;
                workMinutes += work;
                bookedMinutes += booked;
                daily.put(day, work == 0 ? 0 : Math.round(booked * 100f / work));
            }
            rows.add(new DoctorPeriodLoad(d.getFullName(), d.getSpecialty().getName(),
                    d.getRoom() != null ? d.getRoom().getNumber() : "—", workDays, workMinutes, bookedMinutes,
                    (int) own.stream().filter(Appointment::isActive).count(),
                    count(own, AppointmentStatus.COMPLETED), count(own, AppointmentStatus.NO_SHOW),
                    count(own, AppointmentStatus.CANCELLED), daily));
        }
        return excel.doctorLoad(from, to, rows);
    }

    @Transactional(readOnly = true)
    public byte[] revenue(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();
        BigDecimal invoiced = invoices.sumIssuedBetween(start, end);
        BigDecimal outstanding = invoices.findByStatusIn(List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL))
                .stream().map(Invoice::dueAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return excel.revenue(from, to, payments.findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAt(start, end),
                invoiced, outstanding);
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("Укажите период отчёта");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("Дата окончания периода раньше даты начала");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw ApiException.badRequest("Период отчёта не может быть больше года");
        }
    }

    private static int count(List<Appointment> list, AppointmentStatus status) {
        return (int) list.stream().filter(a -> a.getStatus() == status).count();
    }
}
