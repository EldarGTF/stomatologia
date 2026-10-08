package com.stomatologia.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

public class DentalClinicApp extends Application {

    private static Stage primaryStage;

    public static Stage stage() {
        return primaryStage;
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        stage.setTitle("Стоматология CRM");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(new Scene(load("/fxml/login.fxml"), 1280, 800));
        applyTheme(stage.getScene());
        stage.show();
    }

    public static void showLogin() {
        primaryStage.getScene().setRoot(load("/fxml/login.fxml"));
    }

    public static void showMain() {
        primaryStage.getScene().setRoot(load("/fxml/main.fxml"));
    }

    public static Parent load(String fxml) {
        try {
            return FXMLLoader.load(DentalClinicApp.class.getResource(fxml));
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить экран " + fxml, e);
        }
    }

    private static void applyTheme(Scene scene) {
        scene.getStylesheets().add(DentalClinicApp.class.getResource("/styles/app.css").toExternalForm());
    }
}
