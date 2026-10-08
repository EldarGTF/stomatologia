package com.stomatologia.client.dialog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.controller.BookingController;
import com.stomatologia.client.model.AppointmentModels.AppointmentDto;
import com.stomatologia.client.model.PatientModels.PatientDto;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Tables;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.List;

public final class PatientCardDialog {

    private PatientCardDialog() {
    }

    public static void show(PatientDto p) {
        FormDialog card = new FormDialog("Карточка пациента", null);
        card.dialog().setHeaderText(p.fullName());
        card.add("Дата рождения", value(Formats.date(p.birthDate())));
        card.add("Телефон", value(p.phone()));
        card.add("Email", value(p.email()));
        card.add("Адрес", value(p.address()));
        card.add("Примечания", value(p.notes()));
        card.add("Учётная запись", value(p.username() == null ? "нет" : p.username()));

        card.section("История приёмов");
        TableView<AppointmentDto> visits = new TableView<>();
        Tables.sorted(visits, "Дата и время", AppointmentDto::startAt, Formats::dateTime, 130);
        Tables.text(visits, "Врач", AppointmentDto::doctorName, 200);
        Tables.text(visits, "Услуга", AppointmentDto::serviceName, 190);
        Tables.badge(visits, "Статус", a -> a.status().title(), a -> a.status().styleClass(), 110);
        Tables.init(visits, "Загрузка…");
        Tables.onDoubleClick(visits, AppointmentDialogs::history);
        visits.setPrefSize(700, 240);
        card.addWide(visits);
        Fx.async(() -> ApiClient.get().get(ApiClient.query("/api/appointments", "patientId", p.id()),
                new TypeReference<List<AppointmentDto>>() {
                }), list -> {
            visits.getItems().setAll(list.stream()
                    .sorted(Comparator.comparing(AppointmentDto::startAt).reversed()).toList());
            visits.setPlaceholder(new Label("Пациент ещё не записывался на приём"));
        });

        if (Session.hasRole(Role.ADMIN, Role.REGISTRAR)) {
            Button book = new Button("Записать на приём");
            book.getStyleClass().add("primary");
            book.setOnAction(e -> {
                card.dialog().close();
                BookingController.openFor(p.id());
            });
            card.addWide(book);
        }
        card.show();
    }

    private static Label value(String text) {
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.setWrapText(true);
        return label;
    }
}
