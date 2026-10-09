package com.stomatologia.client.controller;

import com.stomatologia.client.model.LeadModels.LeadDto;
import com.stomatologia.client.model.LeadModels.LeadSource;
import com.stomatologia.client.model.LeadModels.LeadStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class LeadsControllerTest {

    private static LeadDto lead(LocalDateTime preferredStart, String preferredText) {
        return new LeadDto(1L, LeadSource.TELEGRAM, LeadStatus.NEW, "Айгерим", null, null, null, null, null,
                preferredStart, preferredText, null, null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), false, null, false);
    }

    private static LeadDto booked(boolean awaitsConfirmation) {
        return new LeadDto(2L, LeadSource.WEBSITE, LeadStatus.BOOKED, "Сайт Тестов", "+77019998877", null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), false,
                awaitsConfirmation ? null : LocalDateTime.now(), awaitsConfirmation);
    }

    @Test
    void preferredSlotWinsOverFreeText() {
        assertThat(LeadsController.preferred(lead(LocalDateTime.of(2026, 10, 12, 11, 30), "утром")))
                .isEqualTo("12.10.2026 11:30");
        assertThat(LeadsController.preferred(lead(null, "в субботу утром"))).isEqualTo("в субботу утром");
        assertThat(LeadsController.preferred(lead(null, null))).isEmpty();
    }

    @Test
    void todayLeadsShowOnlyTime() {
        LocalDateTime today = LocalDateTime.now().with(LocalTime.of(9, 5));
        assertThat(LeadsController.received(today)).isEqualTo("сегодня 09:05");
        assertThat(LeadsController.received(LocalDateTime.of(2026, 1, 15, 14, 0))).isEqualTo("15.01.2026 14:00");
    }

    @Test
    void onlyActiveStatusesAreOpen() {
        assertThat(LeadStatus.NEW.open()).isTrue();
        assertThat(LeadStatus.NEEDS_OPERATOR.open()).isTrue();
        assertThat(LeadStatus.BOOKED.open()).isFalse();
        assertThat(LeadStatus.REJECTED.open()).isFalse();
    }

    @Test
    void unconfirmedOnlineBookingStillNeedsAttention() {
        LeadDto unconfirmed = booked(true);
        assertThat(unconfirmed.needsAttention()).isTrue();
        assertThat(unconfirmed.statusTitle()).isEqualTo("Не подтверждена");
        assertThat(unconfirmed.statusStyle()).isEqualTo("badge-warning");

        LeadDto confirmed = booked(false);
        assertThat(confirmed.needsAttention()).isFalse();
        assertThat(confirmed.statusTitle()).isEqualTo("Записан");
    }
}
