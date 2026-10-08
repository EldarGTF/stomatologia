package com.stomatologia.client.dialog;

import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.ServiceModels.ServiceDto;
import com.stomatologia.client.model.ServiceModels.ServiceRequest;
import com.stomatologia.client.ui.FormDialog;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

import static com.stomatologia.client.ui.Formats.blankToNull;

public final class ServiceFormDialog {

    private ServiceFormDialog() {
    }

    public static boolean show(ServiceDto service) {
        boolean isNew = service == null;
        FormDialog form = new FormDialog(isNew ? "Новая услуга" : "Изменение услуги", "Сохранить");
        TextField name = form.add("Название *", new TextField());
        TextArea description = form.add("Описание", new TextArea());
        description.setPrefRowCount(3);
        description.setWrapText(true);
        TextField price = form.add("Стоимость, ₸ *", new TextField());
        price.setPromptText("например, 4500");
        Spinner<Integer> duration = form.add("Длительность, мин *", new Spinner<>(5, 480, 30, 5));
        duration.setEditable(true);
        CheckBox active = form.add("Статус", new CheckBox("Активна (доступна для записи)"));
        active.setSelected(true);

        if (!isNew) {
            name.setText(service.name());
            description.setText(service.description());
            price.setText(service.price().stripTrailingZeros().toPlainString());
            duration.getValueFactory().setValue(service.durationMinutes());
            active.setSelected(service.active());
        }

        return form.showAndSave(() -> {
            if (name.getText().isBlank()) {
                throw new IllegalArgumentException("Укажите название услуги");
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(price.getText().trim().replace(" ", "").replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Стоимость должна быть числом");
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Стоимость не может быть отрицательной");
            }
            ServiceRequest request = new ServiceRequest(name.getText().trim(), blankToNull(description.getText()),
                    amount, duration.getValue(), active.isSelected());
            if (isNew) {
                ApiClient.get().post("/api/services", request, ServiceDto.class);
            } else {
                ApiClient.get().put("/api/services/" + service.id(), request, ServiceDto.class);
            }
        });
    }
}
