package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.dialog.ServiceFormDialog;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.ServiceModels.ServiceDto;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Tables;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;

public class ServicesController {

    @FXML
    private TableView<ServiceDto> table;
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

    private final ObservableList<ServiceDto> services = FXCollections.observableArrayList();
    private final FilteredList<ServiceDto> filtered = new FilteredList<>(services);
    private boolean admin;

    @FXML
    private void initialize() {
        admin = Session.hasRole(Role.ADMIN);
        Tables.text(table, "Название", ServiceDto::name, 260);
        Tables.text(table, "Описание", ServiceDto::description, 330);
        Tables.sorted(table, "Длительность", ServiceDto::durationMinutes, m -> m + " мин", 110);
        Tables.sorted(table, "Стоимость", ServiceDto::price, Formats::money, 120);
        if (admin) {
            Tables.badge(table, "Статус", s -> s.active() ? "Активна" : "Скрыта",
                    s -> s.active() ? "badge-success" : "badge-muted", 100);
        }
        Tables.init(table, "Услуги не найдены");
        SortedList<ServiceDto> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        for (Button b : List.of(addButton, editButton, deleteButton)) {
            b.setVisible(admin);
            b.setManaged(admin);
        }
        if (admin) {
            Tables.onDoubleClick(table, s -> edit(s));
        }
        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);

        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        load();
    }

    private void load() {
        String path = admin ? "/api/services" : "/api/services?active=true";
        Fx.async(() -> ApiClient.get().get(path, new TypeReference<List<ServiceDto>>() {
        }), list -> {
            services.setAll(list);
            applyFilter();
        });
    }

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.setPredicate(s -> q.isEmpty() || s.name().toLowerCase().contains(q));
        countLabel.setText("Показано: " + filtered.size() + " из " + services.size());
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onAdd() {
        if (ServiceFormDialog.show(null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        edit(table.getSelectionModel().getSelectedItem());
    }

    private void edit(ServiceDto service) {
        if (service != null && ServiceFormDialog.show(service)) {
            load();
        }
    }

    @FXML
    private void onDelete() {
        ServiceDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !Dialogs.confirm("Удалить услугу «" + selected.name() + "»?")) {
            return;
        }
        Fx.run(() -> ApiClient.get().delete("/api/services/" + selected.id()), this::load);
    }
}
