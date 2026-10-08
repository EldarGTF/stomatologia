package com.stomatologia.client.dialog;

import com.stomatologia.client.model.PatientModels.PatientDto;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import javafx.scene.control.Label;

public final class PatientCardDialog {

    private PatientCardDialog() {
    }

    public static void show(PatientDto p) {
        FormDialog card = new FormDialog("Карточка пациента", null);
        card.dialog().setHeaderText(p.fullName());
        card.add("Дата рождения", value(Formats.date(p.birthDate())));
        card.add("Телефон", value(p.phone()));
        card.add("Email", value(p.email()));
        card.add("Адрес", value(p.address()));
        card.add("Примечания", value(p.notes()));
        card.add("Учётная запись", value(p.username() == null ? "нет" : p.username()));
        card.show();
    }

    private static Label value(String text) {
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.setWrapText(true);
        return label;
    }
}
