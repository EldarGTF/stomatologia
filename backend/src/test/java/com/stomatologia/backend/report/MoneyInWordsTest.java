package com.stomatologia.backend.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyInWordsTest {

    private static String words(String amount) {
        return MoneyInWords.tenge(new BigDecimal(amount));
    }

    @Test
    void typicalPrices() {
        assertThat(words("5500.00")).isEqualTo("Пять тысяч пятьсот тенге 00 тиын");
        assertThat(words("1200")).isEqualTo("Одна тысяча двести тенге 00 тиын");
        assertThat(words("18000")).isEqualTo("Восемнадцать тысяч тенге 00 тиын");
    }

    @Test
    void thousandsAndMillionsDependOnNumber() {
        assertThat(words("1")).isEqualTo("Один тенге 00 тиын");
        assertThat(words("2.01")).isEqualTo("Два тенге 01 тиын");
        assertThat(words("2000")).isEqualTo("Две тысячи тенге 00 тиын");
        assertThat(words("5000")).isEqualTo("Пять тысяч тенге 00 тиын");
        assertThat(words("11000")).isEqualTo("Одиннадцать тысяч тенге 00 тиын");
        assertThat(words("22000000")).isEqualTo("Двадцать два миллиона тенге 00 тиын");
    }

    @Test
    void zeroAndLargeAmounts() {
        assertThat(words("0")).isEqualTo("Ноль тенге 00 тиын");
        assertThat(words("1234567.89"))
                .isEqualTo("Один миллион двести тридцать четыре тысячи пятьсот шестьдесят семь тенге 89 тиын");
    }
}
