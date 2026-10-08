package com.stomatologia.client.controller;

import com.stomatologia.client.DentalClinicApp;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.model.AuthModels.LoginRequest;
import com.stomatologia.client.model.AuthModels.LoginResponse;
import com.stomatologia.client.ui.Fx;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;

    @FXML
    private void initialize() {
        Fx.later(usernameField::requestFocus);
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showError("Введите логин и пароль");
            return;
        }
        loginButton.setDisable(true);
        showError(null);
        Fx.async(
                () -> ApiClient.get().post("/api/auth/login", new LoginRequest(username, password), LoginResponse.class),
                response -> {
                    Session.start(response.token(), response.user());
                    DentalClinicApp.showMain();
                },
                ex -> {
                    loginButton.setDisable(false);
                    showError(ex.getMessage());
                });
    }

    private void showError(String message) {
        boolean show = message != null && !message.isBlank();
        errorLabel.setText(show ? message : "");
        errorLabel.setVisible(show);
        errorLabel.setManaged(show);
    }
}
