package com.stomatologia.client.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Выполнение запросов к серверу в фоне, чтобы окно не «зависало». Ошибки показываются через Alert.
 */
public final class Fx {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "api-call");
        t.setDaemon(true);
        return t;
    });

    private Fx() {
    }

    public static <T> void async(Callable<T> call, Consumer<T> onSuccess) {
        async(call, onSuccess, null);
    }

    public static <T> void async(Callable<T> call, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return call.call();
            }
        };
        task.setOnSucceeded(e -> {
            if (onSuccess != null) {
                onSuccess.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (onError != null) {
                onError.accept(ex);
            } else {
                Dialogs.error(ex);
            }
        });
        EXECUTOR.submit(task);
    }

    public static void run(Runnable action, Runnable onSuccess) {
        async(() -> {
            action.run();
            return null;
        }, v -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }

    public static void later(Runnable r) {
        Platform.runLater(r);
    }
}
