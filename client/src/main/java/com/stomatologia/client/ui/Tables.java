package com.stomatologia.client.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Построение колонок таблиц для record-моделей (PropertyValueFactory с record не работает).
 */
public final class Tables {

    private Tables() {
    }

    public static <T> TableColumn<T, String> text(TableView<T> table, String title, Function<T, ?> getter,
                                                  double width) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Formats.nullToEmpty(getter.apply(cd.getValue()))));
        col.setPrefWidth(width);
        table.getColumns().add(col);
        return col;
    }

    /**
     * Колонка с сортировкой по исходному значению (дата, сумма), но отображением в отформатированном виде.
     */
    public static <T, V extends Comparable<? super V>> TableColumn<T, V> sorted(TableView<T> table, String title,
                                                                       Function<T, V> getter,
                                                                       Function<V, String> format, double width) {
        TableColumn<T, V> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(getter.apply(cd.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(V item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        });
        col.setPrefWidth(width);
        table.getColumns().add(col);
        return col;
    }

    /**
     * Колонка-«бейдж» для статусов: текст и CSS-класс вычисляются по строке.
     */
    public static <T> TableColumn<T, T> badge(TableView<T> table, String title, Function<T, String> text,
                                              Function<T, String> styleClass, double width) {
        TableColumn<T, T> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label label = new Label(text.apply(item));
                label.getStyleClass().addAll("badge", styleClass.apply(item));
                setGraphic(label);
            }
        });
        col.setPrefWidth(width);
        col.setSortable(false);
        table.getColumns().add(col);
        return col;
    }

    public static <T> void onDoubleClick(TableView<T> table, Consumer<T> action) {
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    action.accept(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Общие настройки таблицы: колонки растягиваются по ширине, текст для пустой таблицы.
     */
    public static void init(TableView<?> table, String placeholder) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label label = new Label(placeholder);
        label.getStyleClass().add("muted");
        table.setPlaceholder(label);
    }
}
