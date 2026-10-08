package com.stomatologia.client.model;

public final class DoctorModels {

    private DoctorModels() {
    }

    public record DoctorDto(Long id, String fullName, Long specialtyId, String specialtyName,
                            Long roomId, String roomNumber, String phone, String email, boolean active,
                            String username) {

        @Override
        public String toString() {
            return fullName + " — " + specialtyName;
        }
    }

    public record DoctorRequest(String fullName, Long specialtyId, Long roomId, String phone, String email,
                                boolean active, String username, String password) {
    }

    public record SpecialtyDto(Long id, String name) {

        @Override
        public String toString() {
            return name;
        }
    }

    public record RoomDto(Long id, String number, String name) {

        @Override
        public String toString() {
            return "№ " + number + " — " + name;
        }
    }
}
