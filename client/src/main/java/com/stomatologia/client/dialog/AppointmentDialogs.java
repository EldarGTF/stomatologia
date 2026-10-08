package com.stomatologia.client.dialog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.AppointmentModels.AppointmentDto;
import com.stomatologia.client.model.AppointmentModels.AuditDto;
import com.stomatologia.client.model.AppointmentModels.CancelRequest;
import com.stomatologia.client.ui.BookingForm;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Tables;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.List;

/**
 * Диалоги раздела «Приёмы»: новая запись, перенос, отмена с причиной и журнал изменений.
 */
public final class AppointmentDialogs {

    private AppointmentDialogs() {
    }

    public static boolean create() {
        FormDialog dialog = new FormDialog("Новая запись на приём", "Записать");
        BookingForm form = new BookingForm();
        dialog.addWide(form);
        return dialog.showAndSave(() ->
                ApiClient.get().post("/api/appointments", form.request(), AppointmentDto.class));
    }

    public static boolean reschedule(AppointmentDto a) {
        FormDialog dialog = new FormDialog("Перенос записи", "Сохранить");
        dialog.dialog().setHeaderText("Перенос записи: " + a.patientName() + ", сейчас "
                + Formats.dateTime(a.startAt()) + " у врача " + a.doctorName());
        BookingForm form = new BookingForm();
        form.preset(a);
        dialog.addWide(form);
        return dialog.showAndSave(() ->
                ApiClient.get().put("/api/appointments/" + a.id(), form.request(), AppointmentDto.class));
    }

    public static boolean cancel(AppointmentDto a) {
        FormDialog dialog = new FormDialog("Отмена записи", "Отменить запись");
        dialog.dialog().setHeaderText("Отменить запись " + a.patientName() + " на " + Formats.dateTime(a.startAt())
                + "?\nВремя у врача и в кабинете освободится для других пациентов.");
        TextArea reason = dialog.add("Причина", new TextArea());
        reason.setPromptText("Например: пациент заболел");
        reason.setPrefRowCount(3);
        reason.setWrapText(true);
        return dialog.showAndSave(() -> ApiClient.get().post("/api/appointments/" + a.id() + "/cancel",
                new CancelRequest(Formats.blankToNull(reason.getText())), AppointmentDto.class));
    }

    public static void history(AppointmentDto a) {
        FormDialog dialog = new FormDialog("История изменений записи", null);
        dialog.dialog().setHeaderText("История изменений: " + a.patientName() + ", "
                + Formats.dateTime(a.startAt()) + ", " + a.doctorName());
        TableView<AuditDto> table = new TableView<>();
        Tables.sorted(table, "Когда", AuditDto::changedAt, Formats::dateTime, 130);
        Tables.text(table, "Действие", AuditDto::actionTitle, 110);
        Tables.text(table, "Было", AuditDto::oldValue, 260);
        Tables.text(table, "Стало", AuditDto::newValue, 320);
        Tables.text(table, "Кто изменил", AuditDto::changedBy, 170);
        Tables.init(table, "Загрузка…");
        table.setPrefSize(1000, 320);
        dialog.addWide(table);
        Fx.async(() -> ApiClient.get().get("/api/appointments/" + a.id() + "/audit",
                new TypeReference<List<AuditDto>>() {
                }), list -> {
            table.getItems().setAll(list);
            table.setPlaceholder(new Label("Изменений нет"));
        });
        dialog.show();
    }
}
