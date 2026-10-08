package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Doctor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DoctorDtos {

    private DoctorDtos() {
    }

    public record DoctorDto(Long id, String fullName, Long specialtyId, String specialtyName,
                            Long roomId, String roomNumber, String phone, String email, boolean active,
                            String username) {

        public static DoctorDto from(Doctor d) {
            return new DoctorDto(d.getId(), d.getFullName(),
                    d.getSpecialty().getId(), d.getSpecialty().getName(),
                    d.getRoom() != null ? d.getRoom().getId() : null,
                    d.getRoom() != null ? d.getRoom().getNumber() : null,
                    d.getPhone(), d.getEmail(), d.isActive(),
                    d.getUser() != null ? d.getUser().getUsername() : null);
        }
    }

    public record DoctorRequest(
            @NotBlank(message = "Укажите ФИО врача") @Size(max = 150, message = "ФИО слишком длинное") String fullName,
            @NotNull(message = "Выберите специальность") Long specialtyId,
            Long roomId,
            @Size(max = 30, message = "Телефон слишком длинный") String phone,
            @Email(message = "Некорректный email") String email,
            boolean active,
            @Size(max = 50, message = "Логин слишком длинный") String username,
            String password) {
    }

    public record SpecialtyDto(Long id, String name) {
    }
}
