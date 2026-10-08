package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.dialog.PatientCardDialog;
import com.stomatologia.client.dialog.PatientFormDialog;
import com.stomatologia.client.model.PatientModels.PatientDto;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Tables;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

public class PatientsController {

    @FXML
    private TableView<PatientDto> table;
    @FXML
    private TextField searchField;
    @FXML
    private Label countLabel;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button cardButton;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));

    @FXML
    private void initialize() {
        Tables.text(table, "ФИО", PatientDto::fullName, 280);
        Tables.sorted(table, "Дата рождения", PatientDto::birthDate, Formats::date, 120);
        Tables.text(table, "Возраст", p -> age(p.birthDate()), 80);
        Tables.text(table, "Телефон", PatientDto::phone, 150);
        Tables.text(table, "Email", PatientDto::email, 200);
        Tables.text(table, "Учётная запись", PatientDto::username, 130);
        Tables.init(table, "Пациенты не найдены");
        Tables.onDoubleClick(table, this::openCard);

        boolean canEdit = Session.hasRole(Role.ADMIN, Role.REGISTRAR);
        for (Button b : List.of(addButton, editButton, deleteButton)) {
            b.setVisible(canEdit);
            b.setManaged(canEdit);
        }
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        cardButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        searchDelay.setOnFinished(e -> load());
        searchField.textProperty().addListener((obs, o, n) -> searchDelay.playFromStart());
        load();
    }

    private void load() {
        String q = searchField.getText();
        Fx.async(() -> ApiClient.get().get(ApiClient.query("/api/patients", "q", q),
                        new TypeReference<List<PatientDto>>() {
                        }),
                list -> {
                    table.getItems().setAll(list);
                    countLabel.setText("Найдено: " + list.size());
                });
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onAdd() {
        if (PatientFormDialog.show(null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        PatientDto selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && PatientFormDialog.show(selected)) {
            load();
        }
    }

    @FXML
    private void onDelete() {
        PatientDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !Dialogs.confirm("Удалить карточку пациента «" + selected.fullName() + "»?")) {
            return;
        }
        Fx.run(() -> ApiClient.get().delete("/api/patients/" + selected.id()), this::load);
    }

    @FXML
    private void onCard() {
        openCard(table.getSelectionModel().getSelectedItem());
    }

    private void openCard(PatientDto patient) {
        if (patient != null) {
            PatientCardDialog.show(patient);
        }
    }

    private static String age(LocalDate birthDate) {
        return birthDate == null ? "" : String.valueOf(Period.between(birthDate, LocalDate.now()).getYears());
    }
}
