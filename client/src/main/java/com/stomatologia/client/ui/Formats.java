package com.stomatologia.client.ui;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class Formats {

    public static final Locale RU = Locale.forLanguageTag("ru-RU");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(RU);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        MONEY = new DecimalFormat("#,##0.##", symbols);
    }

    private Formats() {
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String time(LocalTime t) {
        return t == null ? "" : TIME.format(t);
    }

    public static String dateTime(LocalDateTime dt) {
        return dt == null ? "" : DATE_TIME.format(dt);
    }

    public static String money(BigDecimal amount) {
        return amount == null ? "" : MONEY.format(amount) + " ₸";
    }

    public static String dayShort(int dayOfWeek) {
        return DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT_STANDALONE, RU);
    }

    public static String dayFull(int dayOfWeek) {
        String s = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL_STANDALONE, RU);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String nullToEmpty(Object o) {
        return o == null ? "" : o.toString();
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Форма слова для числа: 1 событие, 2 события, 5 событий. */
    public static String plural(long n, String one, String few, String many) {
        long tens = Math.abs(n) % 100;
        long units = tens % 10;
        if (tens >= 11 && tens <= 14) {
            return many;
        }
        return units == 1 ? one : units >= 2 && units <= 4 ? few : many;
    }
}
