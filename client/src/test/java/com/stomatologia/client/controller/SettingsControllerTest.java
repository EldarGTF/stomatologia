package com.stomatologia.client.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsControllerTest {

    @ParameterizedTest
    @CsvSource({
            "11200, 12, 1200.00",
            "27500, 12, 2946.43",
            "5000, 0, 0.00"
    })
    void vatIsExtractedFromAmountIncludingIt(String amount, String rate, String vat) {
        assertThat(SettingsController.vatIncluded(new BigDecimal(amount), new BigDecimal(rate)))
                .isEqualByComparingTo(vat);
    }
}
