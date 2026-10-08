package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.dialog.DoctorFormDialog;
import com.stomatologia.client.dialog.WeekScheduleDialog;
import com.stomatologia.client.model.DoctorModels.DoctorDto;
import com.stomatologia.client.model.DoctorModels.SpecialtyDto;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Tables;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;

public class DoctorsController {

    private static final SpecialtyDto ALL = new SpecialtyDto(null, "Все специальности");

    @FXML
    private TableView<DoctorDto> table;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<SpecialtyDto> specialtyFilter;
    @FXML
    private Label countLabel;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button scheduleButton;

    private final ObservableList<DoctorDto> doctors = FXCollections.observableArrayList();
    private final FilteredList<DoctorDto> filtered = new FilteredList<>(doctors);

    @FXML
    private void initialize() {
        Tables.text(table, "ФИО", DoctorDto::fullName, 260);
        Tables.text(table, "Специальность", DoctorDto::specialtyName, 190);
        Tables.text(table, "Кабинет", d -> d.roomNumber() == null ? "" : "№ " + d.roomNumber(), 90);
        Tables.text(table, "Телефон", DoctorDto::phone, 150);
        Tables.text(table, "Email", DoctorDto::email, 210);
        Tables.badge(table, "Статус", d -> d.active() ? "Работает" : "Не работает",
                d -> d.active() ? "badge-success" : "badge-muted", 120);
        Tables.init(table, "Врачи не найдены");
        Tables.onDoubleClick(table, d -> openSchedule(d));
        table.setItems(filtered);

        boolean admin = Session.hasRole(Role.ADMIN);
        for (Button b : List.of(addButton, editButton, deleteButton)) {
            b.setVisible(admin);
            b.setManaged(admin);
        }
        if (admin) {
            Tables.text(table, "Логин", DoctorDto::username, 110);
        }
        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        scheduleButton.disableProperty().bind(noSelection);

        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        specialtyFilter.valueProperty().addListener((obs, o, n) -> applyFilter());

        Fx.async(() -> ApiClient.get().get("/api/specialties", new TypeReference<List<SpecialtyDto>>() {
        }), list -> {
            specialtyFilter.getItems().setAll(ALL);
            specialtyFilter.getItems().addAll(list);
            specialtyFilter.setValue(ALL);
        });
        load();
    }

    private void load() {
        Fx.async(() -> ApiClient.get().get("/api/doctors", new TypeReference<List<DoctorDto>>() {
        }), list -> {
            boolean patient = Session.hasRole(Role.PATIENT);
            doctors.setAll(patient ? list.stream().filter(DoctorDto::active).toList() : list);
            applyFilter();
        });
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        SpecialtyDto spec = specialtyFilter.getValue();
        filtered.setPredicate(d -> (q.isEmpty() || d.fullName().toLowerCase().contains(q))
                && (spec == null || spec.id() == null || spec.id().equals(d.specialtyId())));
        countLabel.setText("Показано: " + filtered.size() + " из " + doctors.size());
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onAdd() {
        if (DoctorFormDialog.show(null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        DoctorDto selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && DoctorFormDialog.show(selected)) {
            load();
        }
    }

    @FXML
    private void onDelete() {
        DoctorDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !Dialogs.confirm("Удалить врача «" + selected.fullName() + "»?")) {
            return;
        }
        Fx.run(() -> ApiClient.get().delete("/api/doctors/" + selected.id()), this::load);
    }

    @FXML
    private void onSchedule() {
        openSchedule(table.getSelectionModel().getSelectedItem());
    }

    private void openSchedule(DoctorDto doctor) {
        if (doctor != null) {
            WeekScheduleDialog.show(doctor.id(), doctor.fullName());
        }
    }
}
