package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Doctor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Данные для сайта онлайн-записи. Отдаются без входа в систему, поэтому в них нет контактов сотрудников
 * и данных других пациентов.
 */
public final class PublicDtos {

    private PublicDtos() {
    }

    public record ClinicInfo(String name, String address, String phone, String email, int bookingHorizonDays,
                             int minLeadHours, int patientCancelHours, LocalDate lastBookableDay) {

        public static ClinicInfo from(ClinicSettings s, LocalDate today) {
            return new ClinicInfo(s.getName(), s.getAddress(), s.getPhone(), s.getEmail(), s.getBookingHorizonDays(),
                    s.getMinLeadHours(), s.getPatientCancelHours(), s.lastBookableDay(today));
        }
    }

    public record ServiceInfo(Long id, String name, String description, BigDecimal price, int durationMinutes) {

        public static ServiceInfo from(ClinicService s) {
            return new ServiceInfo(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getDurationMinutes());
        }
    }

    public record DoctorInfo(Long id, String fullName, String specialtyName, String roomNumber) {

        public static DoctorInfo from(Doctor d) {
            return new DoctorInfo(d.getId(), d.getFullName(), d.getSpecialty().getName(), d.getRoom().getNumber());
        }
    }

    public record HolidayInfo(LocalDate day, String name) {
    }

    /**
     * Онлайн-запись. doctorId == null — «любой врач»: сервер выберет врача, свободного в это время.
     * website — скрытое поле-ловушка: люди его не видят и не заполняют, а боты заполняют.
     */
    public record BookingRequest(
            @NotNull(message = "Выберите услугу") Long serviceId,
            Long doctorId,
            @NotNull(message = "Выберите дату и время") LocalDateTime startAt,
            @NotBlank(message = "Укажите фамилию") @Size(max = 60, message = "Фамилия слишком длинная") String lastName,
            @NotBlank(message = "Укажите имя") @Size(max = 60, message = "Имя слишком длинное") String firstName,
            @NotBlank(message = "Укажите телефон") @Size(max = 30, message = "Телефон слишком длинный") String phone,
            @Size(max = 500, message = "Комментарий слишком длинный") String comment,
            boolean consent,
            String website) {
    }

    /** Запись по личной ссылке: что, где, когда и можно ли ещё отменить онлайн. */
    public record BookingInfo(String token, AppointmentStatus status, String statusTitle, LocalDateTime startAt,
                              LocalDateTime endAt, String doctorName, String specialtyName, String roomNumber,
                              String serviceName, BigDecimal price, String patientName, boolean cancellable,
                              LocalDateTime cancelDeadline, boolean confirmed) {
    }
}
