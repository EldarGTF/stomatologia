package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.ClinicService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ServiceDtos {

    private ServiceDtos() {
    }

    public record ServiceDto(Long id, String name, String description, BigDecimal price, int durationMinutes,
                             boolean active) {

        public static ServiceDto from(ClinicService s) {
            return new ServiceDto(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getDurationMinutes(),
                    s.isActive());
        }
    }

    public record ServiceRequest(
            @NotBlank(message = "Укажите название услуги") @Size(max = 150, message = "Название слишком длинное")
            String name,
            @Size(max = 500, message = "Описание слишком длинное") String description,
            @NotNull(message = "Укажите стоимость") @DecimalMin(value = "0", message = "Стоимость не может быть отрицательной")
            BigDecimal price,
            @NotNull(message = "Укажите длительность")
            @Min(value = 5, message = "Длительность — не меньше 5 минут")
            @Max(value = 480, message = "Длительность — не больше 8 часов")
            Integer durationMinutes,
            boolean active) {
    }
}
