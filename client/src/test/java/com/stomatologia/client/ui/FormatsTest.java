package com.stomatologia.client.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    @ParameterizedTest
    @CsvSource({
            "27500, 27 500 ₸",
            "27500.00, 27 500 ₸",
            "1500.5, '1 500,5 ₸'",
            "0, 0 ₸",
            "4482000, 4 482 000 ₸"
    })
    void moneyIsShownInTengeWithSpaces(String amount, String expected) {
        assertThat(Formats.money(new BigDecimal(amount))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1, событие", "2, события", "4, события", "5, событий", "11, событий", "14, событий",
            "21, событие", "22, события", "25, событий", "111, событий", "0, событий"})
    void wordFormFollowsNumber(long n, String expected) {
        assertThat(Formats.plural(n, "событие", "события", "событий")).isEqualTo(expected);
    }

    @Test
    void emptyValuesBecomeEmptyStrings() {
        assertThat(Formats.money(null)).isEmpty();
        assertThat(Formats.date(null)).isEmpty();
        assertThat(Formats.dateTime(null)).isEmpty();
        assertThat(Formats.nullToEmpty(null)).isEmpty();
    }

    @Test
    void datesUseRussianFormat() {
        assertThat(Formats.date(LocalDate.of(2026, 10, 8))).isEqualTo("08.10.2026");
        assertThat(Formats.time(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(Formats.dateTime(LocalDateTime.of(2026, 10, 8, 14, 30))).isEqualTo("08.10.2026 14:30");
    }

    @Test
    void dayNamesAreRussian() {
        assertThat(Formats.dayFull(1)).isEqualTo("Понедельник");
        assertThat(Formats.dayFull(7)).isEqualTo("Воскресенье");
        assertThat(Formats.dayShort(3)).isEqualTo("ср");
    }

    @Test
    void blankTextBecomesNull() {
        assertThat(Formats.blankToNull("   ")).isNull();
        assertThat(Formats.blankToNull(null)).isNull();
        assertThat(Formats.blankToNull("  Жалоба на боль  ")).isEqualTo("Жалоба на боль");
    }
}
