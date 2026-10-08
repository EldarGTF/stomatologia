package com.stomatologia.client.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class InvoiceModels {

    private InvoiceModels() {
    }

    public enum InvoiceStatus {
        UNPAID("Не оплачен", "badge-warning"),
        PARTIAL("Оплачен частично", "badge-info"),
        PAID("Оплачен", "badge-success"),
        CANCELLED("Аннулирован", "badge-muted");

        private final String title;
        private final String styleClass;

        InvoiceStatus(String title, String styleClass) {
            this.title = title;
            this.styleClass = styleClass;
        }

        public String title() {
            return title;
        }

        public String styleClass() {
            return styleClass;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum PaymentMethod {
        CASH("Наличные"),
        CARD("Банковская карта"),
        TRANSFER("Перевод");

        private final String title;

        PaymentMethod(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public record InvoiceDto(Long id, String number, Long appointmentId, LocalDateTime appointmentStart,
                             Long patientId, String patientName, String doctorName, String serviceName,
                             BigDecimal amount, BigDecimal paidAmount, BigDecimal dueAmount, InvoiceStatus status,
                             LocalDateTime issuedAt, List<PaymentDto> payments) {

        public boolean payable() {
            return status != InvoiceStatus.CANCELLED && dueAmount.signum() > 0;
        }
    }

    public record PaymentDto(Long id, BigDecimal amount, PaymentMethod method, LocalDateTime paidAt,
                             String receivedBy) {
    }

    public record PaymentRequest(BigDecimal amount, PaymentMethod method) {
    }
}
