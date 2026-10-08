package com.stomatologia.client.ui;

import com.stomatologia.client.DentalClinicApp;
import com.stomatologia.client.api.ApiClient;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Сохранение документов и отчётов, сформированных сервером: выбор файла, загрузка и открытие в Word/Excel.
 */
public final class Downloads {

    private static File lastDirectory;

    private Downloads() {
    }

    public static void word(String apiPath, String suggestedName) {
        download(apiPath, suggestedName, "Документ Word (*.docx)", "*.docx");
    }

    public static void excel(String apiPath, String suggestedName) {
        download(apiPath, suggestedName, "Книга Excel (*.xlsx)", "*.xlsx");
    }

    private static void download(String apiPath, String suggestedName, String description, String pattern) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить файл");
        chooser.setInitialFileName(suggestedName.replaceAll("[\\\\/:*?\"<>|]", "_"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
        File dir = lastDirectory != null ? lastDirectory : defaultDirectory();
        if (dir.isDirectory()) {
            chooser.setInitialDirectory(dir);
        }
        File target = chooser.showSaveDialog(DentalClinicApp.stage());
        if (target == null) {
            return;
        }
        lastDirectory = target.getParentFile();
        Fx.async(() -> {
            Files.write(target.toPath(), ApiClient.get().download(apiPath));
            return open(target);
        }, opened -> {
            if (!opened) {
                Dialogs.info("Файл сохранён:\n" + target.getAbsolutePath());
            }
        });
    }

    private static boolean open(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                return true;
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // нет программы для открытия файла — пользователю покажем путь
        }
        return false;
    }

    private static File defaultDirectory() {
        File documents = new File(System.getProperty("user.home"), "Documents");
        return documents.isDirectory() ? documents : new File(System.getProperty("user.home"));
    }
}
