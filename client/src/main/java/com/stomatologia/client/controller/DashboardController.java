package com.stomatologia.client.controller;

import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.AppointmentModels.AppointmentDto;
import com.stomatologia.client.model.DashboardModels.DashboardDto;
import com.stomatologia.client.model.DashboardModels.DayRevenue;
import com.stomatologia.client.model.DashboardModels.DoctorLoad;
import com.stomatologia.client.model.DashboardModels.LeadStats;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Screen;
import com.stomatologia.client.ui.Tables;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Главный экран администратора и регистратора. Все показатели приходят с сервера и считаются по БД.
 */
public class DashboardController {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM");
    private static final int HIGH_LOAD_PERCENT = 85;

    @FXML
    private Label dateLabel;
    @FXML
    private Label updatedLabel;
    @FXML
    private HBox alertBox;
    @FXML
    private Label alertLabel;
    @FXML
    private Label appointmentsLabel;
    @FXML
    private Label appointmentsSub;
    @FXML
    private Label windowsLabel;
    @FXML
    private Label windowsSub;
    @FXML
    private Label revenueLabel;
    @FXML
    private Label revenueSub;
    @FXML
    private Label outstandingLabel;
    @FXML
    private Label leadsLabel;
    @FXML
    private Label leadsSub;
    @FXML
    private BarChart<String, Number> revenueChart;
    @FXML
    private NumberAxis amountAxis;
    @FXML
    private VBox loadBox;
    @FXML
    private TableView<AppointmentDto> upcomingTable;

    private final Timeline autoRefresh = new Timeline(new KeyFrame(Duration.minutes(1), e -> load(false)));

    @FXML
    private void initialize() {
        showAlert(false);
        Tables.text(upcomingTable, "Время", a -> Formats.TIME.format(a.startAt()) + "–" + Formats.TIME.format(a.endAt()), 110);
        Tables.text(upcomingTable, "Пациент", AppointmentDto::patientName, 220);
        Tables.text(upcomingTable, "Телефон", AppointmentDto::patientPhone, 140);
        Tables.text(upcomingTable, "Врач", AppointmentDto::doctorName, 210);
        Tables.text(upcomingTable, "Каб.", AppointmentDto::roomNumber, 60);
        Tables.text(upcomingTable, "Услуга", AppointmentDto::serviceName, 200);
        Tables.init(upcomingTable, "На сегодня больше нет запланированных приёмов");

        amountAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number n) {
                return n.intValue() >= 1000 ? (n.intValue() / 1000) + " тыс." : String.valueOf(n.intValue());
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });

        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
        upcomingTable.sceneProperty().addListener((obs, o, n) -> {
            if (n == null) {
                autoRefresh.stop();
            }
        });
        load(true);
    }

    private void load(boolean manual) {
        Fx.async(() -> ApiClient.get().get("/api/dashboard", DashboardDto.class), this::show, ex -> {
            if (manual) {
                Dialogs.error(ex);
            } else {
                updatedLabel.setText("не удалось обновить: " + ex.getMessage());
            }
        });
    }

    private void show(DashboardDto d) {
        dateLabel.setText("Сегодня " + Formats.dayFull(d.date().getDayOfWeek().getValue()).toLowerCase() + ", "
                + Formats.date(d.date())
                + (d.holidayName() != null ? " · нерабочий день клиники («" + d.holidayName() + "»)" : ""));
        updatedLabel.setText("обновлено в " + Formats.TIME.format(LocalTime.now()));

        appointmentsLabel.setText(String.valueOf(d.appointmentsToday()));
        appointmentsSub.setText("завершено " + d.completed() + " · впереди " + (d.scheduled() - d.awaitingMark())
                + " · неявок " + d.noShow() + " · отменено " + d.cancelled());
        windowsLabel.setText(String.valueOf(d.freeWindows()));
        windowsSub.setText("по " + d.windowMinutes() + " минут до конца рабочего дня");
        revenueLabel.setText(Formats.money(d.revenueToday()));
        revenueSub.setText("с начала месяца: " + Formats.money(d.revenueMonth()));
        outstandingLabel.setText(Formats.money(d.outstanding()));
        LeadStats leads = d.leads();
        leadsLabel.setText(String.valueOf(leads.open()));
        leadsSub.setText("новых сегодня " + leads.newToday() + " · дошли до записи за 30 дней: "
                + leads.conversionPercent() + "%");

        showAlert(d.awaitingMark() > 0);
        alertLabel.setText("Приёмов, которые уже закончились, но не отмечены: " + d.awaitingMark()
                + ". Отметьте «Приём завершён» или «Неявка», чтобы выставить счёт.");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (DayRevenue r : d.revenueByDay()) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(DAY_MONTH.format(r.date()), r.amount());
            series.getData().add(point);
        }
        revenueChart.getData().setAll(series);
        for (int i = 0; i < series.getData().size(); i++) {
            DayRevenue r = d.revenueByDay().get(i);
            Tooltip.install(series.getData().get(i).getNode(),
                    new Tooltip(Formats.date(r.date()) + ": " + Formats.money(r.amount())));
        }

        loadBox.getChildren().clear();
        for (DoctorLoad l : d.doctorLoad()) {
            loadBox.getChildren().add(loadRow(l));
        }
        upcomingTable.getItems().setAll(d.upcoming());
    }

    private void showAlert(boolean visible) {
        alertBox.setVisible(visible);
        alertBox.setManaged(visible);
    }

    private static VBox loadRow(DoctorLoad l) {
        Label name = new Label(l.doctorName());
        name.getStyleClass().add("schedule-doctor");
        Label meta = new Label(l.specialtyName() + (l.roomNumber() != null ? " · каб. " + l.roomNumber() : ""));
        meta.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label percent = new Label(l.working() ? l.loadPercent() + "%" : "выходной");
        percent.getStyleClass().add(l.working() ? "section-title" : "muted");
        HBox header = new HBox(8, new VBox(2, name, meta), spacer, percent);

        if (!l.working()) {
            return new VBox(4, header);
        }
        ProgressBar bar = new ProgressBar(l.loadPercent() / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(l.loadPercent() >= HIGH_LOAD_PERCENT ? "load-high" : "load-normal");
        Label details = new Label(l.appointments() + " " + plural(l.appointments(), "приём", "приёма", "приёмов")
                + " · занято " + hours(l.bookedMinutes()) + " из " + hours(l.workMinutes())
                + " · свободных окон: " + l.freeWindows());
        details.getStyleClass().add("muted");
        return new VBox(4, header, bar, details);
    }

    private static String hours(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " ч" : h + " ч " + m + " мин";
    }

    private static String plural(int n, String one, String few, String many) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        return mod10 == 1 ? one : mod10 >= 2 && mod10 <= 4 ? few : many;
    }

    @FXML
    private void onRefresh() {
        load(true);
    }

    @FXML
    private void onAppointments() {
        MainController.navigate(Screen.APPOINTMENTS);
    }

    @FXML
    private void onLeads() {
        MainController.navigate(Screen.LEADS);
    }

    @FXML
    private void onBook() {
        MainController.navigate(Screen.BOOKING);
    }
}
