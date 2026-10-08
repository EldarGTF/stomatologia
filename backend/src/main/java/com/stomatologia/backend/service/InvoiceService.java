package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.InvoiceStatus;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.dto.InvoiceDtos.InvoiceDto;
import com.stomatologia.backend.dto.InvoiceDtos.PaymentRequest;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.InvoiceRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import com.stomatologia.backend.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Счета и оплата. Счёт выставляется на стоимость услуги приёма: автоматически при завершении приёма
 * или заранее регистратором (предоплата). Отмена приёма или неявка аннулирует неоплаченный счёт.
 */
@Service
public class InvoiceService {

    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceRepository invoices;
    private final AppointmentRepository appointments;
    private final UserRepository users;

    public InvoiceService(InvoiceRepository invoices, AppointmentRepository appointments, UserRepository users) {
        this.invoices = invoices;
        this.appointments = appointments;
        this.users = users;
    }

    public record Filter(LocalDate from, LocalDate to, InvoiceStatus status, Long patientId) {
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> search(Filter filter) {
        AuthUser me = CurrentUser.get();
        Long patientId = me.is(Role.PATIENT) ? me.patientId() : filter.patientId();
        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filter.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                p.add(cb.lessThan(root.get("issuedAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (filter.status() != null) {
                p.add(cb.equal(root.get("status"), filter.status()));
            }
            if (patientId != null) {
                p.add(cb.equal(root.get("appointment").get("patient").get("id"), patientId));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return invoices.findAll(spec, Sort.by(Sort.Direction.DESC, "issuedAt")).stream()
                .map(InvoiceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDto get(Long id) {
        Invoice invoice = find(id);
        checkCanView(invoice);
        return InvoiceDto.from(invoice);
    }

    /**
     * Возвращает счёт приёма, а если его ещё нет — выставляет новый.
     */
    @Transactional
    public InvoiceDto issueForAppointment(Long appointmentId) {
        Appointment a = appointments.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Запись на приём не найдена"));
        Optional<Invoice> existing = invoices.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            return InvoiceDto.from(existing.get());
        }
        if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.NO_SHOW) {
            throw ApiException.conflict("Приём в статусе «" + a.getStatus().title() + "» — счёт не выставляется");
        }
        return InvoiceDto.from(issue(a));
    }

    @Transactional
    public InvoiceDto pay(Long id, PaymentRequest r) {
        Invoice invoice = find(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.conflict("Счёт " + invoice.getNumber() + " аннулирован — оплата невозможна");
        }
        BigDecimal due = invoice.dueAmount();
        if (due.signum() == 0) {
            throw ApiException.conflict("Счёт " + invoice.getNumber() + " уже полностью оплачен");
        }
        if (r.amount().compareTo(due) > 0) {
            throw ApiException.badRequest("Сумма оплаты больше остатка по счёту (" + due.toPlainString() + " ₽)");
        }
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(r.amount());
        payment.setMethod(r.method());
        payment.setReceivedBy(users.getReferenceById(CurrentUser.get().id()));
        invoice.getPayments().add(payment);
        invoice.refreshStatus();
        invoices.saveAndFlush(invoice);
        return InvoiceDto.from(invoice);
    }

    @Transactional
    public InvoiceDto cancel(Long id) {
        Invoice invoice = find(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.conflict("Счёт уже аннулирован");
        }
        if (invoice.paidAmount().signum() > 0) {
            throw ApiException.conflict("По счёту уже есть оплата — аннулировать его нельзя");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoices.saveAndFlush(invoice);
        return InvoiceDto.from(invoice);
    }

    /** Приём завершён — выставляем счёт, если его ещё нет. */
    void onCompleted(Appointment a) {
        if (invoices.findByAppointmentId(a.getId()).isEmpty()) {
            issue(a);
        }
    }

    /** Приём отменён или пациент не пришёл — неоплаченный счёт аннулируется. */
    void onClosedWithoutVisit(Appointment a) {
        invoices.findByAppointmentId(a.getId())
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED && i.paidAmount().signum() == 0)
                .ifPresent(i -> i.setStatus(InvoiceStatus.CANCELLED));
    }

    /** Сменилась услуга приёма — сумма открытого счёта следует за ценой новой услуги. */
    void onServiceChanged(Appointment a) {
        invoices.findByAppointmentId(a.getId())
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .ifPresent(i -> {
                    BigDecimal price = a.getService().getPrice();
                    if (i.paidAmount().compareTo(price) > 0) {
                        throw ApiException.conflict("По счёту " + i.getNumber()
                                + " уже оплачено больше стоимости новой услуги");
                    }
                    i.setAmount(price);
                    i.refreshStatus();
                });
    }

    private Invoice issue(Appointment a) {
        Invoice invoice = new Invoice();
        invoice.setAppointment(a);
        invoice.setAmount(a.getService().getPrice());
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setNumber(number(invoice.getIssuedAt(), a.getId()));
        invoice.refreshStatus();
        return invoices.saveAndFlush(invoice);
    }

    public static String number(LocalDateTime issuedAt, Long appointmentId) {
        return "СЧ-" + NUMBER_DATE.format(issuedAt) + "-" + String.format("%06d", appointmentId);
    }

    Invoice find(Long id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("Счёт не найден"));
    }

    static void checkCanView(Invoice invoice) {
        AuthUser me = CurrentUser.get();
        if (me.is(Role.PATIENT) && !invoice.getAppointment().getPatient().getId().equals(me.patientId())) {
            throw ApiException.forbidden("Нет доступа к этому счёту");
        }
    }
}
