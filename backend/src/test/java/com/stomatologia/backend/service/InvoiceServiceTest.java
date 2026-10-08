package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.InvoiceStatus;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.PaymentMethod;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.dto.InvoiceDtos.PaymentRequest;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.InvoiceRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoices;
    @Mock
    private AppointmentRepository appointments;
    @Mock
    private UserRepository users;

    @InjectMocks
    private InvoiceService service;

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        AuthUser registrar = new AuthUser(2L, "registrar", "Козлова Марина Сергеевна", Role.REGISTRAR, null, null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(registrar, null, List.of()));

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setLastName("Алексеев");
        patient.setFirstName("Игорь");
        Doctor doctor = new Doctor();
        doctor.setFullName("Иванова Елена Петровна");
        ClinicService treatment = new ClinicService();
        treatment.setName("Лечение кариеса");
        Appointment visit = new Appointment();
        visit.setId(42L);
        visit.setPatient(patient);
        visit.setDoctor(doctor);
        visit.setService(treatment);
        visit.setStartAt(LocalDateTime.of(2026, 10, 5, 10, 0));

        invoice = new Invoice();
        invoice.setAppointment(visit);
        invoice.setId(7L);
        invoice.setNumber("СЧ-20261005-000042");
        invoice.setAmount(new BigDecimal("27500.00"));
        invoice.refreshStatus();
        when(invoices.findById(7L)).thenReturn(Optional.of(invoice));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static PaymentRequest payment(String amount) {
        return new PaymentRequest(new BigDecimal(amount), PaymentMethod.CARD);
    }

    private static HttpStatus statusOf(Throwable ex) {
        return ((ApiException) ex).getStatus();
    }

    private void addPayment(String amount) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setMethod(PaymentMethod.CASH);
        invoice.getPayments().add(p);
        invoice.refreshStatus();
    }

    @Test
    void partialPaymentLeavesDebt() {
        var dto = service.pay(7L, payment("10000"));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(dto.dueAmount()).isEqualByComparingTo("17500");
        verify(invoices).saveAndFlush(invoice);
    }

    @Test
    void paymentOfRemainderClosesInvoice() {
        addPayment("10000");

        service.pay(7L, payment("17500"));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.dueAmount()).isEqualByComparingTo("0");
    }

    @Test
    void overpaymentIsRejected() {
        assertThatThrownBy(() -> service.pay(7L, payment("30000")))
                .hasMessageContaining("больше остатка")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(invoice.getPayments()).isEmpty();
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void paidInvoiceCannotBePaidAgain() {
        addPayment("27500");

        assertThatThrownBy(() -> service.pay(7L, payment("100")))
                .hasMessageContaining("уже полностью оплачен")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelledInvoiceCannotBePaid() {
        invoice.setStatus(InvoiceStatus.CANCELLED);

        assertThatThrownBy(() -> service.pay(7L, payment("100")))
                .hasMessageContaining("аннулирован");
    }

    @Test
    void invoiceWithPaymentsCannotBeCancelled() {
        addPayment("5000");

        assertThatThrownBy(() -> service.cancel(7L))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "NO_SHOW"})
    void unpaidInvoiceIsCancelledWhenVisitDidNotHappen(AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(42L);
        a.setStatus(status);
        when(invoices.findByAppointmentId(42L)).thenReturn(Optional.of(invoice));

        service.onClosedWithoutVisit(a);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void prepaidInvoiceSurvivesAppointmentCancellation() {
        addPayment("27500");
        Appointment a = new Appointment();
        a.setId(42L);
        a.setStatus(AppointmentStatus.CANCELLED);
        when(invoices.findByAppointmentId(42L)).thenReturn(Optional.of(invoice));

        service.onClosedWithoutVisit(a);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void invoiceNumberContainsDateAndAppointment() {
        assertThat(InvoiceService.number(LocalDateTime.of(2026, 10, 5, 14, 30), 42L))
                .isEqualTo("СЧ-20261005-000042");
    }
}
