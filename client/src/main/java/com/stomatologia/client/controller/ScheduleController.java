package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.dialog.WeekScheduleDialog;
import com.stomatologia.client.model.DoctorModels.DoctorDto;
import com.stomatologia.client.model.DoctorModels.SpecialtyDto;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.ScheduleModels.ScheduleDto;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Календарь работы врачей: неделя по дням, в ячейке — рабочие часы врача.
 */
public class ScheduleController {

    private static final SpecialtyDto ALL = new SpecialtyDto(null, "Все специальности");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Formats.RU);

    @FXML
    private GridPane grid;
    @FXML
    private Label weekLabel;
    @FXML
    private Label hintLabel;
    @FXML
    private ComboBox<SpecialtyDto> specialtyFilter;

    private LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private List<DoctorDto> doctors = List.of();
    private List<ScheduleDto> schedules = List.of();

    @FXML
    private void initialize() {
        hintLabel.setText(Session.hasRole(Role.ADMIN)
                ? "Нажмите на имя врача, чтобы изменить его график"
                : "Рабочие часы врачей по дням недели");
        specialtyFilter.valueProperty().addListener((obs, o, n) -> render());
        Fx.async(() -> ApiClient.get().get("/api/specialties", new TypeReference<List<SpecialtyDto>>() {
        }), list -> {
            specialtyFilter.getItems().setAll(ALL);
            specialtyFilter.getItems().addAll(list);
            specialtyFilter.setValue(ALL);
        });
        load();
    }

    private void load() {
        Fx.async(() -> new Object[]{
                ApiClient.get().get("/api/doctors", new TypeReference<List<DoctorDto>>() {
                }),
                ApiClient.get().get("/api/schedules", new TypeReference<List<ScheduleDto>>() {
                })
        }, result -> {
            @SuppressWarnings("unchecked")
            List<DoctorDto> d = (List<DoctorDto>) result[0];
            @SuppressWarnings("unchecked")
            List<ScheduleDto> s = (List<ScheduleDto>) result[1];
            doctors = d.stream().filter(DoctorDto::active).toList();
            schedules = s;
            render();
        });
    }

    private void render() {
        LocalDate weekEnd = weekStart.plusDays(6);
        weekLabel.setText(DAY_MONTH.format(weekStart) + " — " + DAY_MONTH.format(weekEnd) + " " + weekEnd.getYear());

        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        ColumnConstraints first = new ColumnConstraints(240);
        grid.getColumnConstraints().add(first);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setHgrow(Priority.ALWAYS);
            c.setMinWidth(100);
            grid.getColumnConstraints().add(c);
        }

        grid.add(header("Врач"), 0, 0);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Label h = header(Formats.dayShort(date.getDayOfWeek().getValue()) + ", " + date.format(DateTimeFormatter.ofPattern("dd.MM")));
            if (date.equals(today)) {
                h.getStyleClass().add("schedule-today");
            }
            grid.add(h, i + 1, 0);
        }

        SpecialtyDto spec = specialtyFilter.getValue();
        Map<Long, Map<Integer, ScheduleDto>> byDoctor = schedules.stream().collect(Collectors.groupingBy(
                ScheduleDto::doctorId, Collectors.toMap(ScheduleDto::dayOfWeek, s -> s)));
        Long ownDoctorId = Session.user().doctorId();

        int row = 1;
        for (DoctorDto doctor : doctors) {
            if (spec != null && spec.id() != null && !spec.id().equals(doctor.specialtyId())) {
                continue;
            }
            grid.add(doctorCell(doctor, Objects.equals(doctor.id(), ownDoctorId)), 0, row);
            Map<Integer, ScheduleDto> week = byDoctor.getOrDefault(doctor.id(), Map.of());
            for (int day = 1; day <= 7; day++) {
                ScheduleDto s = week.get(day);
                Label cell = new Label(s == null ? "выходной"
                        : Formats.time(s.startTime()) + " – " + Formats.time(s.endTime()));
                cell.getStyleClass().addAll("schedule-cell", s == null ? "schedule-cell-off" : "schedule-cell-work");
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setMaxHeight(Double.MAX_VALUE);
                grid.add(cell, day, row);
            }
            row++;
        }
        if (row == 1) {
            Label empty = new Label("Нет врачей для отображения");
            empty.getStyleClass().addAll("schedule-cell", "schedule-cell-off");
            empty.setMaxWidth(Double.MAX_VALUE);
            grid.add(empty, 0, 1, 8, 1);
        }
    }

    private VBox doctorCell(DoctorDto doctor, boolean own) {
        Label name = new Label(doctor.fullName() + (own ? " (вы)" : ""));
        name.getStyleClass().add("schedule-doctor");
        Label spec = new Label(doctor.specialtyName() + (doctor.roomNumber() != null ? " · каб. " + doctor.roomNumber() : ""));
        spec.getStyleClass().add("muted");
        VBox box = new VBox(2, name, spec);
        box.getStyleClass().add("schedule-cell");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxHeight(Double.MAX_VALUE);
        if (Session.hasRole(Role.ADMIN)) {
            box.setCursor(Cursor.HAND);
            box.setOnMouseClicked(e -> {
                if (WeekScheduleDialog.show(doctor.id(), doctor.fullName())) {
                    load();
                }
            });
        }
        return box;
    }

    private static Label header(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("schedule-header");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    @FXML
    private void onPrevWeek() {
        weekStart = weekStart.minusWeeks(1);
        render();
    }

    @FXML
    private void onNextWeek() {
        weekStart = weekStart.plusWeeks(1);
        render();
    }

    @FXML
    private void onThisWeek() {
        weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        render();
    }

    @FXML
    private void onRefresh() {
        load();
    }
}
