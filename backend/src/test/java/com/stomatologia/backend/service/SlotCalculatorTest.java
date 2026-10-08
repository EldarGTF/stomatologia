package com.stomatologia.backend.service;

import com.stomatologia.backend.service.SlotCalculator.Interval;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2030, 3, 4);

    private static LocalDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute);
    }

    @Test
    void emptyDayGivesSlotsThatFitWorkingHours() {
        List<LocalDateTime> starts = SlotCalculator.freeStarts(DAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                30, 15, List.of(), null);

        assertThat(starts).containsExactly(at(9, 0), at(9, 15), at(9, 30));
    }

    @Test
    void busyIntervalsAreSkipped() {
        List<Interval> busy = List.of(new Interval(at(9, 30), at(10, 0)));

        List<LocalDateTime> starts = SlotCalculator.freeStarts(DAY, LocalTime.of(9, 0), LocalTime.of(11, 0),
                30, 15, busy, null);

        assertThat(starts).containsExactly(at(9, 0), at(10, 0), at(10, 15), at(10, 30));
    }

    @Test
    void adjacentAppointmentsDoNotConflict() {
        List<Interval> busy = List.of(new Interval(at(9, 0), at(9, 30)), new Interval(at(10, 0), at(10, 30)));

        List<LocalDateTime> starts = SlotCalculator.freeStarts(DAY, LocalTime.of(9, 0), LocalTime.of(10, 30),
                30, 30, busy, null);

        assertThat(starts).containsExactly(at(9, 30));
    }

    @Test
    void slotsBeforeNotBeforeAreIgnored() {
        List<LocalDateTime> starts = SlotCalculator.freeStarts(DAY, LocalTime.of(9, 0), LocalTime.of(11, 0),
                60, 30, List.of(), at(9, 40));

        assertThat(starts).containsExactly(at(10, 0));
    }

    @Test
    void serviceLongerThanWorkingDayHasNoSlots() {
        List<LocalDateTime> starts = SlotCalculator.freeStarts(DAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                90, 15, List.of(), null);

        assertThat(starts).isEmpty();
    }
}
