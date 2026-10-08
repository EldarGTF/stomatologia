package com.stomatologia.client;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DentalClinicApp extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("Стоматология CRM");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Каркас клиента. Экраны появятся на следующих этапах.");
        subtitle.getStyleClass().add("muted");

        VBox root = new VBox(12, title, subtitle);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setTitle("Стоматология CRM");
        stage.setScene(scene);
        stage.show();
    }
}
