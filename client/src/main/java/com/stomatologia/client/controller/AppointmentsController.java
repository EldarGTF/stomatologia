package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.dialog.AppointmentDialogs;
import com.stomatologia.client.model.AppointmentModels.AppointmentDto;
import com.stomatologia.client.model.AppointmentModels.AppointmentStatus;
import com.stomatologia.client.model.AppointmentModels.StatusRequest;
import com.stomatologia.client.model.DoctorModels.DoctorDto;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Screen;
import com.stomatologia.client.ui.Tables;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class AppointmentsController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label countLabel;
    @FXML
    private Label hintLabel;
    @FXML
    private TableView<AppointmentDto> table;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private ComboBox<DoctorDto> doctorFilter;
    @FXML
    private ComboBox<AppointmentStatus> statusFilter;
    @FXML
    private TextField searchField;
    @FXML
    private Button addButton;
    @FXML
    private Button rescheduleButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button completeButton;
    @FXML
    private Button noShowButton;
    @FXML
    private Button historyButton;

    private final ObservableList<AppointmentDto> appointments = FXCollections.observableArrayList();
    private final FilteredList<AppointmentDto> filtered = new FilteredList<>(appointments);
    private boolean loading;

    @FXML
    private void initialize() {
        Role role = Session.role();
        boolean staff = Session.hasRole(Role.ADMIN, Role.REGISTRAR);
        boolean canBook = staff || role == Role.PATIENT;
        boolean canMark = staff || role == Role.DOCTOR;
        titleLabel.setText(Screen.APPOINTMENTS.title(role));

        Tables.composite(table, "Дата и время", AppointmentsController::period,
                Comparator.comparing(AppointmentDto::startAt), 170).setMinWidth(165);
        if (role != Role.PATIENT) {
            Tables.text(table, "Пациент", AppointmentDto::patientName, 200);
        }
        if (role != Role.DOCTOR) {
            Tables.text(table, "Врач", AppointmentDto::doctorName, 190);
        }
        Tables.text(table, "Каб.", AppointmentDto::roomNumber, 50);
        Tables.text(table, "Услуга", AppointmentDto::serviceName, 190);
        Tables.sorted(table, "Стоимость", a -> a.price() == null ? BigDecimal.ZERO : a.price(), Formats::money, 90);
        Tables.badge(table, "Статус", a -> a.status().title(), a -> a.status().styleClass(), 130).setMinWidth(125);
        Tables.text(table, "Комментарий", AppointmentDto::notes, 150);
        Tables.init(table, "Приёмов за выбранный период нет");
        Tables.onDoubleClick(table, AppointmentDialogs::history);
        SortedList<AppointmentDto> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        show(addButton, canBook);
        show(rescheduleButton, canBook);
        show(cancelButton, canBook);
        show(completeButton, canMark);
        show(noShowButton, canMark);
        if (role == Role.PATIENT) {
            addButton.setText("Записаться");
        }

        doctorFilter.setVisible(role != Role.DOCTOR);
        doctorFilter.setManaged(role != Role.DOCTOR);
        doctorFilter.setButtonCell(doctorCell());
        doctorFilter.setCellFactory(c -> doctorCell());
        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(AppointmentStatus.values());
        statusFilter.setButtonCell(statusCell());
        statusFilter.setCellFactory(c -> statusCell());

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateButtons(n));
        updateButtons(null);

        LocalDate today = LocalDate.now();
        boolean history = role == Role.PATIENT;
        setPeriod(history ? today.minusMonths(3) : today, history ? today.plusMonths(3) : today.plusDays(7));
        fromPicker.valueProperty().addListener((obs, o, n) -> load());
        toPicker.valueProperty().addListener((obs, o, n) -> load());
        doctorFilter.valueProperty().addListener((obs, o, n) -> load());
        statusFilter.valueProperty().addListener((obs, o, n) -> load());
        searchField.textProperty().addListener((obs, o, n) -> applySearch());
        if (role == Role.PATIENT) {
            searchField.setVisible(false);
            searchField.setManaged(false);
        }
        if (role != Role.DOCTOR) {
            loadDoctors();
        }
        load();
    }

    private static ListCell<DoctorDto> doctorCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(DoctorDto d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? "Все врачи" : d.fullName());
            }
        };
    }

    private static ListCell<AppointmentStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AppointmentStatus s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "Все статусы" : s.title());
            }
        };
    }

    private static String period(AppointmentDto a) {
        return Formats.dateTime(a.startAt()) + "–" + Formats.TIME.format(a.endAt());
    }

    private static void show(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void loadDoctors() {
        Fx.async(() -> ApiClient.get().get("/api/doctors", new TypeReference<List<DoctorDto>>() {
        }), list -> {
            doctorFilter.getItems().setAll((DoctorDto) null);
            doctorFilter.getItems().addAll(list);
        });
    }

    private void setPeriod(LocalDate from, LocalDate to) {
        loading = true;
        fromPicker.setValue(from);
        toPicker.setValue(to);
        loading = false;
    }

    private void load() {
        if (loading) {
            return;
        }
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from != null && to != null && to.isBefore(from)) {
            Dialogs.error("Дата окончания периода раньше даты начала");
            return;
        }
        DoctorDto doctor = doctorFilter.getValue();
        AppointmentStatus status = statusFilter.getValue();
        String path = ApiClient.query("/api/appointments", "from", from, "to", to,
                "doctorId", doctor == null ? null : doctor.id(), "status", status == null ? null : status.name());
        AppointmentDto selected = table.getSelectionModel().getSelectedItem();
        Fx.async(() -> ApiClient.get().get(path, new TypeReference<List<AppointmentDto>>() {
        }), list -> {
            appointments.setAll(list);
            applySearch();
            if (selected != null) {
                list.stream().filter(a -> a.id().equals(selected.id())).findFirst()
                        .ifPresent(a -> table.getSelectionModel().select(a));
            }
        });
    }

    private void applySearch() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.setPredicate(a -> q.isEmpty() || a.patientName().toLowerCase().contains(q)
                || (a.patientPhone() != null && a.patientPhone().contains(q)));
        long scheduled = filtered.stream().filter(AppointmentDto::scheduled).count();
        countLabel.setText("Показано: " + filtered.size() + " · запланировано: " + scheduled);
    }

    private void updateButtons(AppointmentDto a) {
        boolean scheduled = a != null && a.scheduled();
        rescheduleButton.setDisable(!scheduled);
        cancelButton.setDisable(!scheduled);
        completeButton.setDisable(!scheduled || !a.started());
        noShowButton.setDisable(!scheduled || !a.started());
        historyButton.setDisable(a == null);
        if (a == null) {
            hintLabel.setText("Выберите приём в таблице. Двойной щелчок — история изменений.");
        } else if (scheduled && !a.started()) {
            hintLabel.setText("Отметить «Приём завершён» или «Неявка» можно после начала приёма.");
        } else if (!scheduled) {
            hintLabel.setText("Приём в статусе «" + a.status().title() + "» — изменения недоступны.");
        } else {
            hintLabel.setText("");
        }
    }

    private AppointmentDto selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onToday() {
        setPeriod(LocalDate.now(), LocalDate.now());
        load();
    }

    @FXML
    private void onWeek() {
        setPeriod(LocalDate.now(), LocalDate.now().plusDays(7));
        load();
    }

    @FXML
    private void onMonth() {
        setPeriod(LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
        load();
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onAdd() {
        if (Session.hasRole(Role.PATIENT)) {
            MainController.navigate(Screen.BOOKING);
        } else if (AppointmentDialogs.create()) {
            load();
        }
    }

    @FXML
    private void onReschedule() {
        AppointmentDto a = selected();
        if (a != null && AppointmentDialogs.reschedule(a)) {
            load();
        }
    }

    @FXML
    private void onCancel() {
        AppointmentDto a = selected();
        if (a != null && AppointmentDialogs.cancel(a)) {
            load();
        }
    }

    @FXML
    private void onComplete() {
        changeStatus(AppointmentStatus.COMPLETED, "Отметить приём как завершённый?");
    }

    @FXML
    private void onNoShow() {
        changeStatus(AppointmentStatus.NO_SHOW, "Отметить неявку пациента?");
    }

    private void changeStatus(AppointmentStatus status, String question) {
        AppointmentDto a = selected();
        if (a == null || !Dialogs.confirm(question + "\n" + a.patientName() + ", " + Formats.dateTime(a.startAt()))) {
            return;
        }
        Fx.run(() -> ApiClient.get().post("/api/appointments/" + a.id() + "/status", new StatusRequest(status),
                AppointmentDto.class), this::load);
    }

    @FXML
    private void onHistory() {
        AppointmentDto a = selected();
        if (a != null) {
            AppointmentDialogs.history(a);
        }
    }
}
