package com.stomatologia.client.model;

import com.stomatologia.client.model.InvoiceModels.InvoiceDto;
import com.stomatologia.client.model.InvoiceModels.InvoiceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceModelsTest {

    private static InvoiceDto invoice(InvoiceStatus status, String due) {
        return new InvoiceDto(1L, "СЧ-20261005-000042", 42L, null, 100L, "Алексеев Игорь", "Иванова Елена Петровна",
                "Лечение кариеса", new BigDecimal("27500"), BigDecimal.ZERO, new BigDecimal(due), status, null,
                List.of());
    }

    @Test
    void invoiceWithDebtCanBePaid() {
        assertThat(invoice(InvoiceStatus.UNPAID, "27500").payable()).isTrue();
        assertThat(invoice(InvoiceStatus.PARTIAL, "17500").payable()).isTrue();
    }

    @Test
    void paidOrCancelledInvoiceCannotBePaid() {
        assertThat(invoice(InvoiceStatus.PAID, "0").payable()).isFalse();
        assertThat(invoice(InvoiceStatus.CANCELLED, "27500").payable()).isFalse();
    }

    @Test
    void everyStatusHasTitleAndBadge() {
        for (InvoiceStatus s : InvoiceStatus.values()) {
            assertThat(s.title()).isNotBlank();
            assertThat(s.styleClass()).startsWith("badge-");
        }
    }
}
