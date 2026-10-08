package com.stomatologia.client.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * Диалог-форма «подпись — поле». Сохранение выполняется по кнопке, при ошибке диалог остаётся открытым,
 * а сообщение показывается рядом с действием пользователя.
 */
public class FormDialog {

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final GridPane grid = new GridPane();
    private final ButtonType saveType;
    private int row;

    public FormDialog(String title, String saveText) {
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        saveType = new ButtonType(saveText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(saveText == null ? "Закрыть" : "Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (saveText == null) {
            dialog.getDialogPane().getButtonTypes().add(cancel);
        } else {
            dialog.getDialogPane().getButtonTypes().addAll(saveType, cancel);
        }
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(130);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(320);
        grid.getColumnConstraints().addAll(labels, fields);
        dialog.getDialogPane().setContent(grid);
        Dialogs.style(dialog);
    }

    public <N extends Node> N add(String label, N field) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        grid.add(l, 0, row);
        grid.add(field, 1, row);
        if (field instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        row++;
        return field;
    }

    public void addWide(Node node) {
        grid.add(node, 0, row, 2, 1);
        row++;
    }

    public void section(String title) {
        Label l = new Label(title);
        l.getStyleClass().add("section-title");
        addWide(l);
    }

    /**
     * Показывает форму; {@code save} вызывается по кнопке сохранения. Возвращает true, если сохранение удалось.
     */
    public boolean showAndSave(Runnable save) {
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        if (saveButton != null) {
            saveButton.addEventFilter(ActionEvent.ACTION, event -> {
                try {
                    save.run();
                } catch (RuntimeException ex) {
                    Dialogs.error(ex);
                    event.consume();
                }
            });
        }
        return dialog.showAndWait().filter(b -> b == saveType).isPresent();
    }

    public void show() {
        dialog.showAndWait();
    }

    public Dialog<ButtonType> dialog() {
        return dialog;
    }
}
