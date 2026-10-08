package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Patient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class PatientDtos {

    private PatientDtos() {
    }

    public record PatientDto(Long id, String lastName, String firstName, String middleName, String fullName,
                             LocalDate birthDate, String phone, String email, String address, String notes,
                             String username) {

        public static PatientDto from(Patient p) {
            return new PatientDto(p.getId(), p.getLastName(), p.getFirstName(), p.getMiddleName(), p.getFullName(),
                    p.getBirthDate(), p.getPhone(), p.getEmail(), p.getAddress(), p.getNotes(),
                    p.getUser() != null ? p.getUser().getUsername() : null);
        }
    }

    public record PatientRequest(
            @NotBlank(message = "Укажите фамилию") @Size(max = 60, message = "Фамилия слишком длинная") String lastName,
            @NotBlank(message = "Укажите имя") @Size(max = 60, message = "Имя слишком длинное") String firstName,
            @Size(max = 60, message = "Отчество слишком длинное") String middleName,
            @Past(message = "Дата рождения должна быть в прошлом") LocalDate birthDate,
            @Pattern(regexp = "^$|^[+0-9 ()-]{5,30}$", message = "Телефон может содержать только цифры, пробелы, +, -, ()")
            String phone,
            @Email(message = "Некорректный email") String email,
            @Size(max = 255, message = "Адрес слишком длинный") String address,
            String notes,
            @Size(max = 50, message = "Логин слишком длинный") String username,
            String password) {
    }
}
