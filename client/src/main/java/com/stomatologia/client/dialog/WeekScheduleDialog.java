package com.stomatologia.client.dialog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.ScheduleModels.ScheduleDto;
import com.stomatologia.client.model.ScheduleModels.WeekRequest;
import com.stomatologia.client.model.ScheduleModels.WorkDay;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * График работы врача на неделю. Администратор может его изменить, остальные — только просмотреть.
 */
public final class WeekScheduleDialog {

    private WeekScheduleDialog() {
    }

    public static boolean show(Long doctorId, String doctorName) {
        List<ScheduleDto> current = ApiClient.get().get("/api/schedules?doctorId=" + doctorId,
                new TypeReference<>() {
                });
        boolean editable = Session.hasRole(Role.ADMIN);
        FormDialog form = new FormDialog("График работы", editable ? "Сохранить" : null);
        form.dialog().setHeaderText("График работы: " + doctorName);

        List<Row> rows = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            int d = day;
            ScheduleDto existing = current.stream().filter(s -> s.dayOfWeek() == d).findFirst().orElse(null);
            Row row = new Row(day, existing, editable);
            rows.add(row);
            form.add(Formats.dayFull(day), row.box);
        }

        if (!editable) {
            form.show();
            return false;
        }
        return form.showAndSave(() -> {
            List<WorkDay> days = new ArrayList<>();
            for (Row row : rows) {
                if (row.works.isSelected()) {
                    if (!row.end.getValue().isAfter(row.start.getValue())) {
                        throw new IllegalArgumentException(
                                Formats.dayFull(row.day) + ": окончание работы должно быть позже начала");
                    }
                    days.add(new WorkDay(row.day, row.start.getValue(), row.end.getValue()));
                }
            }
            ApiClient.get().put("/api/schedules/doctor/" + doctorId, new WeekRequest(days), ScheduleDto[].class);
        });
    }

    private static final class Row {
        final int day;
        final CheckBox works = new CheckBox("Рабочий день");
        final ComboBox<LocalTime> start = timeBox();
        final ComboBox<LocalTime> end = timeBox();
        final HBox box;

        Row(int day, ScheduleDto existing, boolean editable) {
            this.day = day;
            works.setSelected(existing != null);
            start.setValue(existing != null ? existing.startTime() : LocalTime.of(9, 0));
            end.setValue(existing != null ? existing.endTime() : LocalTime.of(18, 0));
            if (editable) {
                start.disableProperty().bind(works.selectedProperty().not());
                end.disableProperty().bind(works.selectedProperty().not());
            } else {
                works.setDisable(true);
                start.setDisable(true);
                end.setDisable(true);
            }
            box = new HBox(10, works, start, new Label("—"), end);
            box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        }
    }

    private static ComboBox<LocalTime> timeBox() {
        ComboBox<LocalTime> box = new ComboBox<>();
        for (LocalTime t = LocalTime.of(7, 0); !t.isAfter(LocalTime.of(22, 0)); t = t.plusMinutes(30)) {
            box.getItems().add(t);
        }
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalTime t) {
                return Formats.time(t);
            }

            @Override
            public LocalTime fromString(String s) {
                return LocalTime.parse(s, Formats.TIME);
            }
        });
        box.setPrefWidth(100);
        return box;
    }
}
