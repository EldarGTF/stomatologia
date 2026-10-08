package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.model.AppointmentModels.AppointmentDto;
import com.stomatologia.client.model.AppointmentModels.AppointmentRequest;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.BookingForm;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Downloads;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Screen;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

public class BookingController {

    /** Пациент, выбранный на другом экране (например, в карточке), для которого открыта запись. */
    private static Long presetPatientId;

    @FXML
    private ScrollPane formScroll;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Button bookButton;
    @FXML
    private VBox upcomingCard;
    @FXML
    private ListView<AppointmentDto> upcomingList;

    private BookingForm form;
    private final boolean patientMode = Session.hasRole(Role.PATIENT);

    public static void openFor(Long patientId) {
        presetPatientId = patientId;
        MainController.navigate(Screen.BOOKING);
    }

    @FXML
    private void initialize() {
        createForm();
        subtitleLabel.setText(patientMode
                ? "Выберите врача, услугу и удобное свободное время"
                : "Свободное время рассчитывается по графику врача с учётом занятости врача и кабинета");
        upcomingCard.setVisible(patientMode);
        upcomingCard.setManaged(patientMode);
        if (patientMode) {
            upcomingList.setPlaceholder(new Label("Предстоящих приёмов нет"));
            upcomingList.setCellFactory(l -> new ListCell<>() {
                @Override
                protected void updateItem(AppointmentDto a, boolean empty) {
                    super.updateItem(a, empty);
                    setText(empty || a == null ? null : Formats.dateTime(a.startAt()) + "\n" + a.doctorName()
                            + "\n" + a.serviceName() + " · каб. " + a.roomNumber());
                }
            });
            loadUpcoming();
        }
    }

    private void createForm() {
        form = new BookingForm();
        formScroll.setContent(form);
        if (presetPatientId != null) {
            form.presetPatient(presetPatientId);
            presetPatientId = null;
        }
    }

    private void loadUpcoming() {
        String path = ApiClient.query("/api/appointments", "from", LocalDate.now(), "status", "SCHEDULED");
        Fx.async(() -> ApiClient.get().get(path, new TypeReference<List<AppointmentDto>>() {
        }), list -> upcomingList.getItems().setAll(list));
    }

    @FXML
    private void onBook() {
        AppointmentRequest request;
        try {
            request = form.request();
        } catch (IllegalArgumentException e) {
            Dialogs.error(e.getMessage());
            return;
        }
        bookButton.setDisable(true);
        Fx.async(() -> ApiClient.get().post("/api/appointments", request, AppointmentDto.class), a -> {
            bookButton.setDisable(false);
            form.resetAfterBooking();
            if (patientMode) {
                loadUpcoming();
            }
            boolean print = Dialogs.offer("Запись создана",
                    (patientMode ? "Вы записаны" : a.patientName() + " записан(а)")
                    + " на " + Formats.dateTime(a.startAt()) + "\nВрач: " + a.doctorName() + ", кабинет "
                    + a.roomNumber() + "\nУслуга: " + a.serviceName() + " — " + Formats.money(a.price()),
                    "Талон (Word)");
            if (print) {
                Downloads.word("/api/reports/appointments/" + a.id() + "/ticket",
                        AppointmentsController.ticketFileName(a));
            }
        }, ex -> {
            bookButton.setDisable(false);
            Dialogs.error(ex);
            form.resetAfterBooking();
        });
    }

    @FXML
    private void onClear() {
        createForm();
    }
}
