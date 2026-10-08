package com.stomatologia.client.controller;

import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Downloads;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Screen;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ReportsController {

    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private Label periodLabel;

    @FXML
    private void initialize() {
        fromPicker.valueProperty().addListener((obs, o, n) -> updatePeriodLabel());
        toPicker.valueProperty().addListener((obs, o, n) -> updatePeriodLabel());
        onThisMonth();
    }

    private void setPeriod(LocalDate from, LocalDate to) {
        fromPicker.setValue(from);
        toPicker.setValue(to);
    }

    private void updatePeriodLabel() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null) {
            periodLabel.setText("Укажите обе даты");
        } else if (to.isBefore(from)) {
            periodLabel.setText("Дата окончания раньше даты начала");
        } else {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            periodLabel.setText(Formats.date(from) + " — " + Formats.date(to) + " · дней: " + days);
        }
    }

    /** Возвращает false и показывает ошибку, если период задан неверно. */
    private boolean periodValid() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null) {
            Dialogs.error("Укажите период отчёта");
            return false;
        }
        if (to.isBefore(from)) {
            Dialogs.error("Дата окончания периода раньше даты начала");
            return false;
        }
        return true;
    }

    @FXML
    private void onLoadReport() {
        if (periodValid()) {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            Downloads.excel(ApiClient.query("/api/reports/doctor-load", "from", from, "to", to),
                    "Загрузка врачей " + Formats.date(from) + "-" + Formats.date(to) + ".xlsx");
        }
    }

    @FXML
    private void onRevenueReport() {
        if (periodValid()) {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            Downloads.excel(ApiClient.query("/api/reports/revenue", "from", from, "to", to),
                    "Выручка " + Formats.date(from) + "-" + Formats.date(to) + ".xlsx");
        }
    }

    @FXML
    private void onWeek() {
        setPeriod(LocalDate.now().minusDays(6), LocalDate.now());
    }

    @FXML
    private void onThisMonth() {
        setPeriod(LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    @FXML
    private void onLastMonth() {
        LocalDate first = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        setPeriod(first, first.plusMonths(1).minusDays(1));
    }

    @FXML
    private void onThirtyDays() {
        setPeriod(LocalDate.now().minusDays(29), LocalDate.now());
    }

    @FXML
    private void onAppointments() {
        MainController.navigate(Screen.APPOINTMENTS);
    }

    @FXML
    private void onPayments() {
        MainController.navigate(Screen.PAYMENTS);
    }
}
