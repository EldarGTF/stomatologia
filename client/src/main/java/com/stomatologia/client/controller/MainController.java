package com.stomatologia.client.controller;

import com.stomatologia.client.DentalClinicApp;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Notifier;
import com.stomatologia.client.ui.Screen;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;

/**
 * Главное окно: боковое меню по роли пользователя и область содержимого.
 */
public class MainController {

    private static MainController instance;

    @FXML
    private VBox navBox;
    @FXML
    private StackPane contentPane;
    @FXML
    private Label userLabel;
    @FXML
    private Label roleLabel;

    private final Map<Screen, Button> buttons = new EnumMap<>(Screen.class);

    /**
     * Позволяет экранам переключать раздел, например «Записать» из карточки пациента.
     */
    public static void navigate(Screen screen) {
        if (instance != null) {
            instance.open(screen);
        }
    }

    @FXML
    private void initialize() {
        instance = this;
        Role role = Session.role();
        userLabel.setText(Session.user().fullName());
        roleLabel.setText(role.title());

        for (Screen screen : Screen.values()) {
            if (!screen.allowedFor(role)) {
                continue;
            }
            Button button = new Button(screen.title(role));
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(e -> open(screen));
            buttons.put(screen, button);
            navBox.getChildren().add(button);
        }
        open(Screen.home(role));
        if (role == Role.ADMIN || role == Role.REGISTRAR) {
            Notifier.start(DentalClinicApp.stage());
        }
    }

    private void open(Screen screen) {
        buttons.values().forEach(b -> b.getStyleClass().remove("active"));
        Button button = buttons.get(screen);
        if (button != null) {
            button.getStyleClass().add("active");
        }
        contentPane.getChildren().setAll(loadView(screen));
    }

    private Node loadView(Screen screen) {
        if (Screen.class.getResource(screen.fxml()) == null) {
            return placeholder(screen);
        }
        try {
            return DentalClinicApp.load(screen.fxml());
        } catch (RuntimeException e) {
            Dialogs.error("Не удалось открыть раздел «" + screen.title(Session.role()) + "»: " + e.getMessage());
            return placeholder(screen);
        }
    }

    private Node placeholder(Screen screen) {
        Label title = new Label(screen.title(Session.role()));
        title.getStyleClass().add("page-title");
        Label text = new Label("Раздел находится в разработке");
        text.getStyleClass().add("muted");
        VBox box = new VBox(8, title, text);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @FXML
    private void onLogout() {
        if (Dialogs.confirm("Выйти из системы?")) {
            Notifier.stop();
            Session.clear();
            instance = null;
            DentalClinicApp.showLogin();
        }
    }
}
