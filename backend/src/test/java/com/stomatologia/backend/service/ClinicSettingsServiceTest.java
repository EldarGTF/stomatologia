package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Holiday;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.dto.SettingsDtos.HolidayRequest;
import com.stomatologia.backend.dto.SettingsDtos.SettingsRequest;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.ClinicSettingsRepository;
import com.stomatologia.backend.repository.HolidayRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicSettingsServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 16);

    @Mock
    private ClinicSettingsRepository settingsRepo;
    @Mock
    private HolidayRepository holidays;
    @Mock
    private AppointmentRepository appointments;
    @Mock
    private UserRepository users;

    @InjectMocks
    private ClinicSettingsService service;

    private ClinicSettings settings;
    private User admin;

    @BeforeEach
    void setUp() {
        AuthUser me = new AuthUser(1L, "admin", "Администратор", Role.ADMIN, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(me, null, List.of()));
        admin = new User();
        admin.setFullName("Администратор");
        when(users.getReferenceById(1L)).thenReturn(admin);

        settings = new ClinicSettings();
        settings.setName("Клиника «Улыбка»");
        settings.setAddress("г. Павлодар");
        settings.setPhone("+7 (7182) 00-00-00");
        when(settingsRepo.findById(ClinicSettings.ID)).thenReturn(Optional.of(settings));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static SettingsRequest request(int slotStep, String prefix, BigDecimal prepayment) {
        return new SettingsRequest("Клиника «Улыбка»", "г. Павлодар", "+7 (7182) 00-00-00", " ",
                "123456789012", null, null, null, slotStep, 60, 1, 24, prefix, false, new BigDecimal("12"),
                prepayment);
    }

    private static HttpStatus statusOf(Throwable ex) {
        return ((ApiException) ex).getStatus();
    }

    @Test
    void unsupportedSlotStepIsRejected() {
        assertThatThrownBy(() -> service.update(request(25, "СЧ", null)))
                .hasMessageContaining("10, 15, 20 или 30")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(settingsRepo, never()).saveAndFlush(any());
    }

    @Test
    void changesAreSavedWithAuthor() {
        var dto = service.update(request(20, "УЛ", new BigDecimal("50000")));

        assertThat(settings.getSlotStepMinutes()).isEqualTo(20);
        assertThat(settings.getInvoicePrefix()).isEqualTo("УЛ");
        assertThat(settings.getBin()).isEqualTo("123456789012");
        assertThat(settings.getEmail()).as("пустой email хранится как null").isNull();
        assertThat(settings.requiresPrepayment(new BigDecimal("50000"))).isTrue();
        assertThat(settings.requiresPrepayment(new BigDecimal("49999.99"))).isFalse();
        assertThat(dto.updatedBy()).isEqualTo("Администратор");
        verify(settingsRepo).saveAndFlush(settings);
    }

    @Test
    void unchangedSettingsAreNotSaved() {
        settings.setBin("123456789012");

        service.update(request(15, "СЧ", null));

        verify(settingsRepo, never()).saveAndFlush(any());
    }

    @Test
    void duplicateHolidayIsRejected() {
        Holiday existing = new Holiday();
        existing.setDay(DAY);
        existing.setName("День Независимости");
        when(holidays.findByDay(DAY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addHoliday(new HolidayRequest(DAY, "Выходной")))
                .hasMessageContaining("16.12.2026 уже отмечен")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void newHolidayReportsAppointmentsAlreadyScheduled() {
        when(holidays.findByDay(DAY)).thenReturn(Optional.empty());
        Appointment scheduled = new Appointment();
        scheduled.setStartAt(DAY.atTime(10, 0));
        Appointment cancelled = new Appointment();
        cancelled.setStartAt(DAY.atTime(11, 0));
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        when(appointments.findActiveBetween(any(), any())).thenReturn(List.of(scheduled, cancelled));

        var dto = service.addHoliday(new HolidayRequest(DAY, "  День Независимости "));

        assertThat(dto.name()).isEqualTo("День Независимости");
        assertThat(dto.scheduledAppointments()).isEqualTo(1);
    }
}
