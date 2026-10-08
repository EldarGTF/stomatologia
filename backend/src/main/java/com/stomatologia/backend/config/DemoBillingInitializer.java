package com.stomatologia.backend.config;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.PaymentMethod;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.InvoiceRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.service.InvoiceService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Демонстрационные счета: по завершённым приёмам (в основном оплачены) и часть предоплат по будущим.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoBillingInitializer implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(DemoBillingInitializer.class);

    private final InvoiceRepository invoices;
    private final AppointmentRepository appointments;
    private final UserRepository users;

    public DemoBillingInitializer(InvoiceRepository invoices, AppointmentRepository appointments,
                                  UserRepository users) {
        this.invoices = invoices;
        this.appointments = appointments;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (invoices.count() > 0 || appointments.count() == 0) {
            return;
        }
        Random random = new Random(7);
        User registrar = users.findByUsername("registrar").orElse(null);
        int count = 0;
        for (Appointment a : appointments.findAll(Sort.by("startAt"))) {
            if (a.getStatus() == AppointmentStatus.COMPLETED) {
                Invoice invoice = invoice(a, a.getEndAt());
                int roll = random.nextInt(100);
                if (roll < 82) {
                    pay(invoice, invoice.getAmount(), a.getEndAt().plusMinutes(5 + random.nextInt(15)), random,
                            registrar);
                } else if (roll < 90) {
                    pay(invoice, invoice.getAmount().divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN),
                            a.getEndAt().plusMinutes(10), random, registrar);
                }
                save(invoice);
                count++;
            } else if (a.getStatus() == AppointmentStatus.SCHEDULED && random.nextInt(100) < 15) {
                Invoice invoice = invoice(a, a.getCreatedAt());
                if (random.nextBoolean()) {
                    pay(invoice, invoice.getAmount(), a.getCreatedAt().plusMinutes(3), random, registrar);
                }
                save(invoice);
                count++;
            }
        }
        log.info("Создано демонстрационных счетов: {}", count);
    }

    private static Invoice invoice(Appointment a, LocalDateTime issuedAt) {
        Invoice invoice = new Invoice();
        invoice.setAppointment(a);
        invoice.setAmount(a.getService().getPrice());
        invoice.setIssuedAt(issuedAt);
        invoice.setNumber(InvoiceService.number(issuedAt, a.getId()));
        return invoice;
    }

    private static void pay(Invoice invoice, BigDecimal amount, LocalDateTime at, Random random, User by) {
        if (amount.signum() <= 0) {
            return;
        }
        int roll = random.nextInt(100);
        Payment p = new Payment();
        p.setInvoice(invoice);
        p.setAmount(amount);
        p.setMethod(roll < 35 ? PaymentMethod.CASH : roll < 90 ? PaymentMethod.CARD : PaymentMethod.TRANSFER);
        p.setPaidAt(at);
        p.setReceivedBy(by);
        invoice.getPayments().add(p);
    }

    private void save(Invoice invoice) {
        invoice.refreshStatus();
        invoices.save(invoice);
    }
}
