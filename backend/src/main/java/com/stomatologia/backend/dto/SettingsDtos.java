package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Holiday;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsDto(String name, String address, String phone, String email,
                              String bin, String bankName, String iik, String bik,
                              int slotStepMinutes, int bookingHorizonDays, int minLeadHours, int patientCancelHours,
                              String invoicePrefix, boolean vatEnabled, BigDecimal vatRate,
                              BigDecimal prepaymentThreshold, LocalDateTime updatedAt, String updatedBy) {

        public static SettingsDto from(ClinicSettings s) {
            return new SettingsDto(s.getName(), s.getAddress(), s.getPhone(), s.getEmail(),
                    s.getBin(), s.getBankName(), s.getIik(), s.getBik(),
                    s.getSlotStepMinutes(), s.getBookingHorizonDays(), s.getMinLeadHours(), s.getPatientCancelHours(),
                    s.getInvoicePrefix(), s.isVatEnabled(), s.getVatRate(), s.getPrepaymentThreshold(),
                    s.getUpdatedAt(), s.getUpdatedBy() != null ? s.getUpdatedBy().getFullName() : null);
        }
    }

    public record SettingsRequest(
            @NotBlank(message = "Укажите название клиники") @Size(max = 200, message = "Название слишком длинное")
            String name,
            @NotBlank(message = "Укажите адрес клиники") @Size(max = 300, message = "Адрес слишком длинный")
            String address,
            @NotBlank(message = "Укажите телефон клиники") @Size(max = 50, message = "Телефон слишком длинный")
            String phone,
            @Email(message = "Некорректный email клиники") @Size(max = 100, message = "Email слишком длинный")
            String email,
            @Pattern(regexp = "\\d{12}", message = "БИН должен состоять из 12 цифр")
            String bin,
            @Size(max = 200, message = "Название банка слишком длинное")
            String bankName,
            @Pattern(regexp = "KZ\\d{2}[A-Z0-9]{16}", message = "ИИК — 20 символов: KZ, 2 цифры и 16 букв или цифр")
            String iik,
            @Pattern(regexp = "[A-Z0-9]{8,11}", message = "БИК — от 8 до 11 латинских букв и цифр")
            String bik,
            @NotNull(message = "Выберите шаг записи") Integer slotStepMinutes,
            @NotNull(message = "Укажите, на сколько дней вперёд можно записаться")
            @Min(value = 7, message = "Запись вперёд — не меньше 7 дней")
            @Max(value = 365, message = "Запись вперёд — не больше 365 дней")
            Integer bookingHorizonDays,
            @NotNull(message = "Укажите минимальное время до приёма")
            @Min(value = 0, message = "Минимальное время до приёма не может быть отрицательным")
            @Max(value = 72, message = "Минимальное время до приёма — не больше 72 часов")
            Integer minLeadHours,
            @NotNull(message = "Укажите срок отмены записи пациентом")
            @Min(value = 0, message = "Срок отмены не может быть отрицательным")
            @Max(value = 168, message = "Срок отмены — не больше 168 часов (7 дней)")
            Integer patientCancelHours,
            @NotBlank(message = "Укажите префикс номера счёта")
            @Pattern(regexp = "[\\p{L}\\d]{1,10}", message = "Префикс счёта — от 1 до 10 букв или цифр")
            String invoicePrefix,
            @NotNull Boolean vatEnabled,
            @NotNull(message = "Укажите ставку НДС")
            @DecimalMin(value = "0", message = "Ставка НДС не может быть отрицательной")
            @DecimalMax(value = "99.99", message = "Некорректная ставка НДС")
            @Digits(integer = 2, fraction = 2, message = "Некорректная ставка НДС")
            BigDecimal vatRate,
            @DecimalMin(value = "0.01", message = "Порог предоплаты должен быть больше нуля")
            @Digits(integer = 8, fraction = 2, message = "Некорректный порог предоплаты")
            BigDecimal prepaymentThreshold) {
    }

    /** Нерабочий день; scheduledAppointments — сколько запланированных приёмов попало на эту дату. */
    public record HolidayDto(Long id, LocalDate day, String name, int scheduledAppointments) {

        public static HolidayDto from(Holiday h, int scheduledAppointments) {
            return new HolidayDto(h.getId(), h.getDay(), h.getName(), scheduledAppointments);
        }
    }

    public record HolidayRequest(
            @NotNull(message = "Укажите дату") LocalDate day,
            @NotBlank(message = "Укажите название") @Size(max = 100, message = "Название слишком длинное") String name) {
    }
}
