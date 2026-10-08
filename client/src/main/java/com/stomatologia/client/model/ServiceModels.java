package com.stomatologia.client.model;

import java.math.BigDecimal;

public final class ServiceModels {

    private ServiceModels() {
    }

    public record ServiceDto(Long id, String name, String description, BigDecimal price, int durationMinutes,
                             boolean active) {

        @Override
        public String toString() {
            return name + " (" + durationMinutes + " мин)";
        }
    }

    public record ServiceRequest(String name, String description, BigDecimal price, Integer durationMinutes,
                                 boolean active) {
    }
}
