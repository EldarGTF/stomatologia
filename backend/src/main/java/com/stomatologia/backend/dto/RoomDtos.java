package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Room;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record RoomDto(Long id, String number, String name) {

        public static RoomDto from(Room r) {
            return new RoomDto(r.getId(), r.getNumber(), r.getName());
        }
    }
}
