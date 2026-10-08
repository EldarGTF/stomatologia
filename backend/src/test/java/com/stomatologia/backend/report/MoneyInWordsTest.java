package com.stomatologia.backend.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyInWordsTest {

    private static String words(String amount) {
        return MoneyInWords.rubles(new BigDecimal(amount));
    }

    @Test
    void typicalPrices() {
        assertThat(words("5500.00")).isEqualTo("Пять тысяч пятьсот рублей 00 копеек");
        assertThat(words("1200")).isEqualTo("Одна тысяча двести рублей 00 копеек");
        assertThat(words("18000")).isEqualTo("Восемнадцать тысяч рублей 00 копеек");
    }

    @Test
    void endingsDependOnNumber() {
        assertThat(words("1")).isEqualTo("Один рубль 00 копеек");
        assertThat(words("2.01")).isEqualTo("Два рубля 01 копейка");
        assertThat(words("11.12")).isEqualTo("Одиннадцать рублей 12 копеек");
        assertThat(words("22.03")).isEqualTo("Двадцать два рубля 03 копейки");
        assertThat(words("2000")).isEqualTo("Две тысячи рублей 00 копеек");
    }

    @Test
    void zeroAndLargeAmounts() {
        assertThat(words("0")).isEqualTo("Ноль рублей 00 копеек");
        assertThat(words("1234567.89"))
                .isEqualTo("Один миллион двести тридцать четыре тысячи пятьсот шестьдесят семь рублей 89 копеек");
    }
}
