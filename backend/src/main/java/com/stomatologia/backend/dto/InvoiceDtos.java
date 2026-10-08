package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.InvoiceStatus;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record InvoiceDto(Long id, String number, Long appointmentId, LocalDateTime appointmentStart,
                             Long patientId, String patientName, String doctorName, String serviceName,
                             BigDecimal amount, BigDecimal paidAmount, BigDecimal dueAmount, InvoiceStatus status,
                             LocalDateTime issuedAt, List<PaymentDto> payments) {

        public static InvoiceDto from(Invoice i) {
            Appointment a = i.getAppointment();
            return new InvoiceDto(i.getId(), i.getNumber(), a.getId(), a.getStartAt(),
                    a.getPatient().getId(), a.getPatient().getFullName(), a.getDoctor().getFullName(),
                    a.getService().getName(), i.getAmount(), i.paidAmount(), i.dueAmount(), i.getStatus(),
                    i.getIssuedAt(), i.getPayments().stream().map(PaymentDto::from).toList());
        }
    }

    public record PaymentDto(Long id, BigDecimal amount, PaymentMethod method, LocalDateTime paidAt,
                             String receivedBy) {

        public static PaymentDto from(Payment p) {
            return new PaymentDto(p.getId(), p.getAmount(), p.getMethod(), p.getPaidAt(),
                    p.getReceivedBy() != null ? p.getReceivedBy().getFullName() : null);
        }
    }

    public record PaymentRequest(
            @NotNull(message = "Укажите сумму")
            @DecimalMin(value = "0.01", message = "Сумма оплаты должна быть больше нуля")
            @Digits(integer = 8, fraction = 2, message = "Некорректная сумма")
            BigDecimal amount,
            @NotNull(message = "Выберите способ оплаты") PaymentMethod method) {
    }
}
