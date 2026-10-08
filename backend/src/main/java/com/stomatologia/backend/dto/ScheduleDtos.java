package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Schedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record ScheduleDto(Long id, Long doctorId, String doctorName, int dayOfWeek, LocalTime startTime,
                              LocalTime endTime) {

        public static ScheduleDto from(Schedule s) {
            return new ScheduleDto(s.getId(), s.getDoctor().getId(), s.getDoctor().getFullName(), s.getDayOfWeek(),
                    s.getStartTime(), s.getEndTime());
        }
    }

    public record WorkDay(
            @NotNull @Min(value = 1, message = "День недели: 1..7") @Max(value = 7, message = "День недели: 1..7")
            Integer dayOfWeek,
            @NotNull(message = "Укажите начало работы") LocalTime startTime,
            @NotNull(message = "Укажите окончание работы") LocalTime endTime) {
    }

    /**
     * Полная рабочая неделя врача: дни, которых нет в списке, считаются выходными.
     */
    public record WeekRequest(@NotNull @Valid List<WorkDay> days) {
    }
}
