package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Room;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record RoomDto(Long id, String number, String name) {

        public static RoomDto from(Room r) {
            return new RoomDto(r.getId(), r.getNumber(), r.getName());
        }
    }

    public record RoomRequest(
            @NotBlank(message = "Укажите номер кабинета") @Size(max = 20, message = "Номер слишком длинный") String number,
            @NotBlank(message = "Укажите название кабинета") @Size(max = 100, message = "Название слишком длинное")
            String name) {
    }
}
