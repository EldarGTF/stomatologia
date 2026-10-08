package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.dialog.InvoiceDialogs;
import com.stomatologia.client.model.InvoiceModels.InvoiceDto;
import com.stomatologia.client.model.InvoiceModels.InvoiceStatus;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Downloads;
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
import java.util.List;
import java.util.function.Function;

public class PaymentsController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label countLabel;
    @FXML
    private Label issuedLabel;
    @FXML
    private Label paidLabel;
    @FXML
    private Label dueLabel;
    @FXML
    private TableView<InvoiceDto> table;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private ComboBox<InvoiceStatus> statusFilter;
    @FXML
    private TextField searchField;
    @FXML
    private Button payButton;
    @FXML
    private Button detailsButton;
    @FXML
    private Button wordButton;
    @FXML
    private Button cancelButton;

    private final ObservableList<InvoiceDto> invoices = FXCollections.observableArrayList();
    private final FilteredList<InvoiceDto> filtered = new FilteredList<>(invoices);
    private final boolean patientMode = Session.hasRole(Role.PATIENT);
    private boolean settingPeriod;

    @FXML
    private void initialize() {
        Role role = Session.role();
        titleLabel.setText(Screen.PAYMENTS.title(role));
        boolean staff = Session.hasRole(Role.ADMIN, Role.REGISTRAR);
        boolean admin = Session.hasRole(Role.ADMIN);

        Tables.text(table, "Номер", InvoiceDto::number, 165).setMinWidth(160);
        Tables.sorted(table, "Выставлен", InvoiceDto::issuedAt, Formats::dateTime, 125).setMinWidth(120);
        if (patientMode) {
            Tables.text(table, "Врач", InvoiceDto::doctorName, 180);
        } else {
            Tables.text(table, "Пациент", InvoiceDto::patientName, 200);
        }
        Tables.text(table, "Услуга", InvoiceDto::serviceName, 180);
        Tables.sorted(table, "Сумма", InvoiceDto::amount, Formats::money, 95);
        Tables.sorted(table, "Оплачено", InvoiceDto::paidAmount, Formats::money, 95);
        Tables.sorted(table, "Остаток", InvoiceDto::dueAmount, Formats::money, 95);
        Tables.badge(table, "Статус", i -> i.status().title(), i -> i.status().styleClass(), 140).setMinWidth(135);
        Tables.init(table, "Счетов за выбранный период нет");
        Tables.onDoubleClick(table, i -> {
            if (staff && i.payable()) {
                pay(i);
            } else {
                InvoiceDialogs.details(i);
            }
        });
        SortedList<InvoiceDto> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        if (patientMode) {
            searchField.setPromptText("Номер счёта");
        }
        payButton.setVisible(staff);
        payButton.setManaged(staff);
        cancelButton.setVisible(admin);
        cancelButton.setManaged(admin);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateButtons(n));
        updateButtons(null);

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(InvoiceStatus.values());
        statusFilter.setButtonCell(statusCell());
        statusFilter.setCellFactory(c -> statusCell());

        settingPeriod = true;
        fromPicker.setValue(patientMode ? null : LocalDate.now().minusDays(30));
        toPicker.setValue(patientMode ? null : LocalDate.now());
        settingPeriod = false;
        fromPicker.valueProperty().addListener((obs, o, n) -> load());
        toPicker.valueProperty().addListener((obs, o, n) -> load());
        statusFilter.valueProperty().addListener((obs, o, n) -> load());
        searchField.textProperty().addListener((obs, o, n) -> applySearch());
        load();
    }

    private static ListCell<InvoiceStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(InvoiceStatus s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "Все статусы" : s.title());
            }
        };
    }

    private void updateButtons(InvoiceDto i) {
        payButton.setDisable(i == null || !i.payable());
        detailsButton.setDisable(i == null);
        wordButton.setDisable(i == null);
        cancelButton.setDisable(i == null || i.status() == InvoiceStatus.CANCELLED || i.paidAmount().signum() > 0);
    }

    private void load() {
        if (settingPeriod) {
            return;
        }
        InvoiceStatus status = statusFilter.getValue();
        String path = ApiClient.query("/api/invoices", "from", fromPicker.getValue(), "to", toPicker.getValue(),
                "status", status == null ? null : status.name());
        Long selectedId = table.getSelectionModel().getSelectedItem() == null ? null
                : table.getSelectionModel().getSelectedItem().id();
        Fx.async(() -> ApiClient.get().get(path, new TypeReference<List<InvoiceDto>>() {
        }), list -> {
            invoices.setAll(list);
            applySearch();
            if (selectedId != null) {
                filtered.stream().filter(i -> i.id().equals(selectedId)).findFirst()
                        .ifPresent(i -> table.getSelectionModel().select(i));
            }
        });
    }

    private void applySearch() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.setPredicate(i -> q.isEmpty() || i.patientName().toLowerCase().contains(q)
                || i.number().toLowerCase().contains(q));
        List<InvoiceDto> active = filtered.stream().filter(i -> i.status() != InvoiceStatus.CANCELLED).toList();
        issuedLabel.setText(Formats.money(sum(active, InvoiceDto::amount)));
        paidLabel.setText(Formats.money(sum(active, InvoiceDto::paidAmount)));
        dueLabel.setText(Formats.money(sum(active, InvoiceDto::dueAmount)));
        long unpaid = active.stream().filter(InvoiceDto::payable).count();
        countLabel.setText("Счетов: " + filtered.size() + " · ожидают оплаты: " + unpaid);
    }

    private static BigDecimal sum(List<InvoiceDto> list, Function<InvoiceDto, BigDecimal> field) {
        return list.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private InvoiceDto selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private void pay(InvoiceDto invoice) {
        if (InvoiceDialogs.pay(invoice)) {
            load();
        }
    }

    @FXML
    private void onPay() {
        InvoiceDto i = selected();
        if (i != null) {
            pay(i);
        }
    }

    @FXML
    private void onDetails() {
        InvoiceDto i = selected();
        if (i != null) {
            InvoiceDialogs.details(i);
        }
    }

    @FXML
    private void onWord() {
        InvoiceDto i = selected();
        if (i != null) {
            Downloads.word("/api/reports/invoices/" + i.id(), "Счёт " + i.number() + ".docx");
        }
    }

    @FXML
    private void onCancel() {
        InvoiceDto i = selected();
        if (i == null || !Dialogs.confirm("Аннулировать счёт " + i.number() + " на " + Formats.money(i.amount()) + "?")) {
            return;
        }
        Fx.run(() -> ApiClient.get().post("/api/invoices/" + i.id() + "/cancel", null, InvoiceDto.class), this::load);
    }

    @FXML
    private void onRefresh() {
        load();
    }
}
