package com.stomatologia.client.dialog;

import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.PatientModels.PatientDto;
import com.stomatologia.client.model.PatientModels.PatientRequest;
import com.stomatologia.client.ui.FormDialog;
import javafx.scene.control.DatePicker;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import static com.stomatologia.client.ui.Formats.blankToNull;

public final class PatientFormDialog {

    private PatientFormDialog() {
    }

    /**
     * Форма добавления (patient == null) или изменения пациента. Возвращает true, если данные сохранены.
     */
    public static boolean show(PatientDto patient) {
        boolean isNew = patient == null;
        FormDialog form = new FormDialog(isNew ? "Новый пациент" : "Изменение данных пациента", "Сохранить");

        TextField lastName = form.add("Фамилия *", new TextField());
        TextField firstName = form.add("Имя *", new TextField());
        TextField middleName = form.add("Отчество", new TextField());
        DatePicker birthDate = form.add("Дата рождения", new DatePicker());
        birthDate.setPromptText("дд.мм.гггг");
        TextField phone = form.add("Телефон", new TextField());
        phone.setPromptText("+7 900 000-00-00");
        TextField email = form.add("Email", new TextField());
        TextField address = form.add("Адрес", new TextField());
        TextArea notes = form.add("Примечания", new TextArea());
        notes.setPrefRowCount(3);
        notes.setWrapText(true);

        form.section("Доступ в систему (необязательно)");
        TextField username = form.add("Логин", new TextField());
        PasswordField password = form.add(isNew ? "Пароль" : "Новый пароль", new PasswordField());
        password.setPromptText(isNew ? "не менее 6 символов" : "оставьте пустым, чтобы не менять");

        if (!isNew) {
            lastName.setText(patient.lastName());
            firstName.setText(patient.firstName());
            middleName.setText(patient.middleName());
            birthDate.setValue(patient.birthDate());
            phone.setText(patient.phone());
            email.setText(patient.email());
            address.setText(patient.address());
            notes.setText(patient.notes());
            username.setText(patient.username());
        }

        return form.showAndSave(() -> {
            if (lastName.getText().isBlank() || firstName.getText().isBlank()) {
                throw new IllegalArgumentException("Фамилия и имя обязательны");
            }
            PatientRequest request = new PatientRequest(lastName.getText().trim(), firstName.getText().trim(),
                    blankToNull(middleName.getText()), birthDate.getValue(), blankToNull(phone.getText()),
                    blankToNull(email.getText()), blankToNull(address.getText()), blankToNull(notes.getText()),
                    blankToNull(username.getText()), blankToNull(password.getText()));
            if (isNew) {
                ApiClient.get().post("/api/patients", request, PatientDto.class);
            } else {
                ApiClient.get().put("/api/patients/" + patient.id(), request, PatientDto.class);
            }
        });
    }
}
