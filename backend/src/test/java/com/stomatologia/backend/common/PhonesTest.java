package com.stomatologia.backend.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhonesTest {

    @ParameterizedTest
    @ValueSource(strings = {"8 701 123 45 67", "+7 (701) 123-45-67", "87011234567", "7011234567", " +77011234567 "})
    void kazakhstanNumbersAreNormalized(String phone) {
        assertThat(Phones.normalize(phone)).isEqualTo("+77011234567");
        assertThat(Phones.lastTenDigits(phone)).isEqualTo("7011234567");
    }

    @Test
    void foreignOrShortNumbersAreKeptAsIs() {
        assertThat(Phones.normalize(" +49 30 1234567 ")).isEqualTo("+49 30 1234567");
        assertThat(Phones.normalize("112")).isEqualTo("112");
        assertThat(Phones.lastTenDigits("112")).isNull();
    }

    @Test
    void blankIsNull() {
        assertThat(Phones.normalize("  ")).isNull();
        assertThat(Phones.normalize(null)).isNull();
    }

    @Test
    void onlyFullKazakhstanNumbersAreValidForOnlineBooking() {
        assertThat(Phones.isValid("8 (701) 555-12-34")).isTrue();
        assertThat(Phones.isValid("+7 701 555 12 34")).isTrue();
        assertThat(Phones.isValid("555-12-34")).isFalse();
        assertThat(Phones.isValid("+49 30 1234567")).isFalse();
        assertThat(Phones.isValid(null)).isFalse();
    }
}
