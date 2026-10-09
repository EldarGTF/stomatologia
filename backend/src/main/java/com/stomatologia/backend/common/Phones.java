package com.stomatologia.backend.common;

/**
 * Телефоны Казахстана: приводит «8 701 123 45 67», «+7 (701) 123-45-67» и «7011234567» к виду +77011234567.
 */
public final class Phones {

    private Phones() {
    }

    /** Нормализованный номер или исходная строка без пробелов по краям, если номер не похож на казахстанский. */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11 && (digits.startsWith("7") || digits.startsWith("8"))) {
            return "+7" + digits.substring(1);
        }
        if (digits.length() == 10) {
            return "+7" + digits;
        }
        return phone.trim();
    }

    /** Последние 10 цифр номера для поиска совпадений; null, если цифр меньше 10. */
    public static String lastTenDigits(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 10 ? null : digits.substring(digits.length() - 10);
    }
}
