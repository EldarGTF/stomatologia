package com.stomatologia.backend.report;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Сумма прописью для счетов: «Пять тысяч пятьсот тенге 00 тиын».
 */
public final class MoneyInWords {

    private static final String[] UNITS_MALE = {"", "один", "два", "три", "четыре", "пять", "шесть", "семь",
            "восемь", "девять"};
    private static final String[] UNITS_FEMALE = {"", "одна", "две", "три", "четыре", "пять", "шесть", "семь",
            "восемь", "девять"};
    private static final String[] TEENS = {"десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
            "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"};
    private static final String[] TENS = {"", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят",
            "семьдесят", "восемьдесят", "девяносто"};
    private static final String[] HUNDREDS = {"", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот",
            "семьсот", "восемьсот", "девятьсот"};

    private MoneyInWords() {
    }

    /** «Тенге» и «тиын» в русском языке не склоняются. */
    public static String tenge(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        long tenge = value.longValue();
        int tiyn = value.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();

        StringBuilder sb = new StringBuilder();
        if (tenge == 0) {
            sb.append("ноль");
        } else {
            appendGroup(sb, tenge / 1_000_000_000 % 1000, false, "миллиард", "миллиарда", "миллиардов");
            appendGroup(sb, tenge / 1_000_000 % 1000, false, "миллион", "миллиона", "миллионов");
            appendGroup(sb, tenge / 1000 % 1000, true, "тысяча", "тысячи", "тысяч");
            appendGroup(sb, tenge % 1000, false, "", "", "");
        }
        String words = sb.toString().trim().replaceAll("\\s+", " ");
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " тенге "
                + String.format("%02d", tiyn) + " тиын";
    }

    private static void appendGroup(StringBuilder sb, long n, boolean female, String one, String few, String many) {
        if (n == 0) {
            return;
        }
        int h = (int) (n / 100);
        int rest = (int) (n % 100);
        sb.append(' ').append(HUNDREDS[h]);
        if (rest >= 10 && rest < 20) {
            sb.append(' ').append(TEENS[rest - 10]);
        } else {
            sb.append(' ').append(TENS[rest / 10]);
            sb.append(' ').append(female ? UNITS_FEMALE[rest % 10] : UNITS_MALE[rest % 10]);
        }
        if (!one.isEmpty()) {
            sb.append(' ').append(plural(n, one, few, many));
        }
    }

    static String plural(long n, String one, String few, String many) {
        long mod100 = n % 100;
        long mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        if (mod10 == 1) {
            return one;
        }
        return mod10 >= 2 && mod10 <= 4 ? few : many;
    }
}
