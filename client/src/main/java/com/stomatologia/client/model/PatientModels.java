package com.stomatologia.client.model;

import java.time.LocalDate;

public final class PatientModels {

    private PatientModels() {
    }

    public record PatientDto(Long id, String lastName, String firstName, String middleName, String fullName,
                             LocalDate birthDate, String phone, String email, String address, String notes,
                             String username) {

        @Override
        public String toString() {
            return phone == null ? fullName : fullName + " (" + phone + ")";
        }
    }

    public record PatientRequest(String lastName, String firstName, String middleName, LocalDate birthDate,
                                 String phone, String email, String address, String notes,
                                 String username, String password) {
    }
}
