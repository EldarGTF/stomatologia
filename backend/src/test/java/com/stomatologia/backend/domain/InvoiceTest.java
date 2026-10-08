package com.stomatologia.backend.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTest {

    private static Invoice invoice(String amount) {
        Invoice invoice = new Invoice();
        invoice.setAmount(new BigDecimal(amount));
        invoice.refreshStatus();
        return invoice;
    }

    private static void pay(Invoice invoice, String amount) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setMethod(PaymentMethod.CARD);
        invoice.getPayments().add(p);
        invoice.refreshStatus();
    }

    @Test
    void newInvoiceIsUnpaid() {
        Invoice invoice = invoice("4500.00");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.dueAmount()).isEqualByComparingTo("4500");
    }

    @Test
    void partialAndFullPayment() {
        Invoice invoice = invoice("4500.00");

        pay(invoice, "2000");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(invoice.dueAmount()).isEqualByComparingTo("2500");

        pay(invoice, "2500");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.dueAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cancelledInvoiceStaysCancelledAndHasNoDebt() {
        Invoice invoice = invoice("1200.00");
        invoice.setStatus(InvoiceStatus.CANCELLED);

        invoice.refreshStatus();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(invoice.dueAmount()).isEqualByComparingTo("0");
    }

    @Test
    void freeServiceIsPaidImmediately() {
        assertThat(invoice("0.00").getStatus()).isEqualTo(InvoiceStatus.PAID);
    }
}
