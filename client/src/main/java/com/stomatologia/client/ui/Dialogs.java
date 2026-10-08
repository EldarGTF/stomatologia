package com.stomatologia.client.ui;

import com.stomatologia.client.DentalClinicApp;
import com.stomatologia.client.api.ApiException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Optional;

public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Throwable ex) {
        String message = ex instanceof ApiException api ? api.getMessage()
                : ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        error(message);
    }

    public static void error(String message) {
        Alert alert = alert(Alert.AlertType.ERROR, "Ошибка", message);
        alert.showAndWait();
    }

    public static void info(String message) {
        Alert alert = alert(Alert.AlertType.INFORMATION, "Готово", message);
        alert.showAndWait();
    }

    public static boolean confirm(String message) {
        Alert alert = alert(Alert.AlertType.CONFIRMATION, "Подтверждение", message);
        ButtonType yes = new ButtonType("Да", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    /**
     * Подключает общую тему и владельца окна к любому диалогу.
     */
    public static void style(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(Dialogs.class.getResource("/styles/app.css").toExternalForm());
        if (DentalClinicApp.stage() != null && dialog.getOwner() == null) {
            dialog.initOwner(DentalClinicApp.stage());
        }
    }

    private static Alert alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        style(alert);
        return alert;
    }
}
