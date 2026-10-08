package com.stomatologia.client.ui;

import com.stomatologia.client.model.Role;

import java.util.EnumSet;
import java.util.Set;

import static com.stomatologia.client.model.Role.ADMIN;
import static com.stomatologia.client.model.Role.DOCTOR;
import static com.stomatologia.client.model.Role.PATIENT;
import static com.stomatologia.client.model.Role.REGISTRAR;

/**
 * Разделы главного меню и роли, которым они доступны.
 */
public enum Screen {
    DASHBOARD("Dashboard", "dashboard", ADMIN, REGISTRAR),
    PATIENTS("Пациенты", "patients", ADMIN, REGISTRAR, DOCTOR),
    DOCTORS("Врачи", "doctors", ADMIN, REGISTRAR, PATIENT),
    SCHEDULE("Расписание", "schedule", ADMIN, REGISTRAR, DOCTOR),
    BOOKING("Запись на приём", "booking", ADMIN, REGISTRAR, PATIENT),
    APPOINTMENTS("Приёмы", "appointments", ADMIN, REGISTRAR, DOCTOR, PATIENT),
    SERVICES("Услуги", "services", ADMIN, REGISTRAR, DOCTOR, PATIENT),
    PAYMENTS("Оплата", "payments", ADMIN, REGISTRAR, PATIENT),
    REPORTS("Отчёты", "reports", ADMIN, REGISTRAR),
    SETTINGS("Настройки", "settings", ADMIN);

    private final String title;
    private final String view;
    private final Set<Role> roles;

    Screen(String title, String view, Role first, Role... rest) {
        this.title = title;
        this.view = view;
        this.roles = EnumSet.of(first, rest);
    }

    public String title(Role role) {
        if (role == DOCTOR || role == PATIENT) {
            if (this == APPOINTMENTS) {
                return "Мои приёмы";
            }
            if (this == PAYMENTS) {
                return "Мои счета";
            }
        }
        return title;
    }

    public String fxml() {
        return "/fxml/" + view + ".fxml";
    }

    public boolean allowedFor(Role role) {
        return roles.contains(role);
    }

    public static Screen home(Role role) {
        return switch (role) {
            case ADMIN, REGISTRAR -> DASHBOARD;
            case DOCTOR -> APPOINTMENTS;
            case PATIENT -> BOOKING;
        };
    }
}
