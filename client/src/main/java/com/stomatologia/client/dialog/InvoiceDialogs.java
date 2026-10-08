package com.stomatologia.client.dialog;

import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.InvoiceModels.InvoiceDto;
import com.stomatologia.client.model.InvoiceModels.PaymentDto;
import com.stomatologia.client.model.InvoiceModels.PaymentMethod;
import com.stomatologia.client.model.InvoiceModels.PaymentRequest;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Tables;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

/**
 * Диалоги раздела «Оплата»: приём оплаты по счёту и подробности счёта с историей платежей.
 */
public final class InvoiceDialogs {

    private InvoiceDialogs() {
    }

    public static boolean pay(InvoiceDto invoice) {
        FormDialog form = new FormDialog("Приём оплаты", "Принять оплату");
        form.dialog().setHeaderText("Счёт " + invoice.number() + " — " + invoice.patientName());
        summary(form, invoice);
        form.section("Оплата");
        TextField amount = form.add("Сумма, ₽ *", new TextField(invoice.dueAmount().stripTrailingZeros().toPlainString()));
        ComboBox<PaymentMethod> method = form.add("Способ оплаты *", new ComboBox<>());
        method.getItems().setAll(PaymentMethod.values());
        method.setValue(PaymentMethod.CARD);
        return form.showAndSave(() -> {
            BigDecimal value;
            try {
                value = new BigDecimal(amount.getText().trim().replace(" ", "").replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Сумма должна быть числом");
            }
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("Сумма оплаты должна быть больше нуля");
            }
            if (value.compareTo(invoice.dueAmount()) > 0) {
                throw new IllegalArgumentException("Сумма больше остатка по счёту: " + Formats.money(invoice.dueAmount()));
            }
            ApiClient.get().post("/api/invoices/" + invoice.id() + "/payments",
                    new PaymentRequest(value, method.getValue()), InvoiceDto.class);
        });
    }

    public static void details(InvoiceDto invoice) {
        FormDialog form = new FormDialog("Счёт", null);
        form.dialog().setHeaderText("Счёт " + invoice.number() + " — " + invoice.status().title());
        summary(form, invoice);
        form.section("Платежи");
        TableView<PaymentDto> payments = new TableView<>();
        Tables.sorted(payments, "Дата", PaymentDto::paidAt, Formats::dateTime, 130);
        Tables.sorted(payments, "Сумма", PaymentDto::amount, Formats::money, 110);
        Tables.text(payments, "Способ", p -> p.method().title(), 140);
        Tables.text(payments, "Принял", PaymentDto::receivedBy, 200);
        Tables.init(payments, "Оплат по счёту ещё не было");
        payments.getItems().setAll(invoice.payments());
        payments.setPrefSize(600, 180);
        form.addWide(payments);
        form.show();
    }

    private static void summary(FormDialog form, InvoiceDto invoice) {
        form.add("Пациент", value(invoice.patientName()));
        form.add("Приём", value(Formats.dateTime(invoice.appointmentStart()) + ", " + invoice.doctorName()));
        form.add("Услуга", value(invoice.serviceName()));
        form.add("Выставлен", value(Formats.dateTime(invoice.issuedAt())));
        form.add("Сумма счёта", value(Formats.money(invoice.amount())));
        form.add("Оплачено", value(Formats.money(invoice.paidAmount())));
        Label due = form.add("К оплате", value(Formats.money(invoice.dueAmount())));
        due.getStyleClass().add("section-title");
    }

    private static Label value(String text) {
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.setWrapText(true);
        return label;
    }
}
