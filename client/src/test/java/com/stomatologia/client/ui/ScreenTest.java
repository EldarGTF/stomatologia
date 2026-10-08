package com.stomatologia.client.ui;

import com.stomatologia.client.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenTest {

    private static Screen[] allowed(Role role) {
        return Arrays.stream(Screen.values()).filter(s -> s.allowedFor(role)).toArray(Screen[]::new);
    }

    @Test
    void adminSeesEverySection() {
        assertThat(allowed(Role.ADMIN)).containsExactly(Screen.values());
    }

    @Test
    void reportsAndDashboardOnlyForAdministration() {
        assertThat(Screen.REPORTS.allowedFor(Role.REGISTRAR)).isTrue();
        assertThat(Screen.REPORTS.allowedFor(Role.DOCTOR)).isFalse();
        assertThat(Screen.REPORTS.allowedFor(Role.PATIENT)).isFalse();
        assertThat(Screen.DASHBOARD.allowedFor(Role.DOCTOR)).isFalse();
        assertThat(Screen.DASHBOARD.allowedFor(Role.PATIENT)).isFalse();
    }

    @Test
    void clinicSettingsOnlyForAdmin() {
        assertThat(Screen.SETTINGS.allowedFor(Role.ADMIN)).isTrue();
        assertThat(Screen.SETTINGS.allowedFor(Role.REGISTRAR)).isFalse();
        assertThat(Screen.SETTINGS.allowedFor(Role.DOCTOR)).isFalse();
        assertThat(Screen.SETTINGS.allowedFor(Role.PATIENT)).isFalse();
    }

    @Test
    void patientWorkplace() {
        assertThat(allowed(Role.PATIENT)).containsExactly(
                Screen.DOCTORS, Screen.BOOKING, Screen.APPOINTMENTS, Screen.SERVICES, Screen.PAYMENTS);
    }

    @Test
    void doctorDoesNotBookOrTakePayments() {
        assertThat(Screen.BOOKING.allowedFor(Role.DOCTOR)).isFalse();
        assertThat(Screen.PAYMENTS.allowedFor(Role.DOCTOR)).isFalse();
        assertThat(Screen.APPOINTMENTS.allowedFor(Role.DOCTOR)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void homeScreenIsAvailableToItsRole(Role role) {
        assertThat(Screen.home(role).allowedFor(role)).isTrue();
    }

    @Test
    void personalTitlesForDoctorAndPatient() {
        assertThat(Screen.APPOINTMENTS.title(Role.DOCTOR)).isEqualTo("Мои приёмы");
        assertThat(Screen.PAYMENTS.title(Role.PATIENT)).isEqualTo("Мои счета");
        assertThat(Screen.APPOINTMENTS.title(Role.REGISTRAR)).isEqualTo("Приёмы");
    }

    @ParameterizedTest
    @EnumSource(Screen.class)
    void everyScreenHasFxmlLayout(Screen screen) {
        assertThat(Screen.class.getResource(screen.fxml())).as(screen.fxml()).isNotNull();
    }
}
