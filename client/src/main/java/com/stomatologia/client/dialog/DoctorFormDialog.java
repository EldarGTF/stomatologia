package com.stomatologia.client.dialog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.DoctorModels.DoctorDto;
import com.stomatologia.client.model.DoctorModels.DoctorRequest;
import com.stomatologia.client.model.DoctorModels.RoomDto;
import com.stomatologia.client.model.DoctorModels.SpecialtyDto;
import com.stomatologia.client.ui.FormDialog;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Objects;

import static com.stomatologia.client.ui.Formats.blankToNull;

public final class DoctorFormDialog {

    private DoctorFormDialog() {
    }

    public static boolean show(DoctorDto doctor) {
        boolean isNew = doctor == null;
        List<SpecialtyDto> specialties = ApiClient.get().get("/api/specialties", new TypeReference<>() {
        });
        List<RoomDto> rooms = ApiClient.get().get("/api/rooms", new TypeReference<>() {
        });

        FormDialog form = new FormDialog(isNew ? "Новый врач" : "Изменение данных врача", "Сохранить");
        TextField fullName = form.add("ФИО *", new TextField());
        ComboBox<SpecialtyDto> specialty = form.add("Специальность *", new ComboBox<>());
        specialty.getItems().setAll(specialties);
        ComboBox<RoomDto> room = form.add("Кабинет", new ComboBox<>());
        room.getItems().setAll(rooms);
        TextField phone = form.add("Телефон", new TextField());
        TextField email = form.add("Email", new TextField());
        CheckBox active = form.add("Статус", new CheckBox("Работает"));
        active.setSelected(true);

        form.section("Учётная запись врача");
        TextField username = form.add(isNew ? "Логин *" : "Логин", new TextField());
        PasswordField password = form.add(isNew ? "Пароль *" : "Новый пароль", new PasswordField());
        password.setPromptText(isNew ? "не менее 6 символов" : "оставьте пустым, чтобы не менять");

        if (!isNew) {
            fullName.setText(doctor.fullName());
            specialty.setValue(specialties.stream()
                    .filter(s -> s.id().equals(doctor.specialtyId())).findFirst().orElse(null));
            room.setValue(rooms.stream()
                    .filter(r -> Objects.equals(r.id(), doctor.roomId())).findFirst().orElse(null));
            phone.setText(doctor.phone());
            email.setText(doctor.email());
            active.setSelected(doctor.active());
            username.setText(doctor.username());
        }

        return form.showAndSave(() -> {
            if (fullName.getText().isBlank()) {
                throw new IllegalArgumentException("Укажите ФИО врача");
            }
            if (specialty.getValue() == null) {
                throw new IllegalArgumentException("Выберите специальность");
            }
            if (isNew && (username.getText().isBlank() || password.getText().isBlank())) {
                throw new IllegalArgumentException("Для нового врача укажите логин и пароль");
            }
            DoctorRequest request = new DoctorRequest(fullName.getText().trim(), specialty.getValue().id(),
                    room.getValue() == null ? null : room.getValue().id(), blankToNull(phone.getText()),
                    blankToNull(email.getText()), active.isSelected(), blankToNull(username.getText()),
                    blankToNull(password.getText()));
            if (isNew) {
                ApiClient.get().post("/api/doctors", request, DoctorDto.class);
            } else {
                ApiClient.get().put("/api/doctors/" + doctor.id(), request, DoctorDto.class);
            }
        });
    }
}
