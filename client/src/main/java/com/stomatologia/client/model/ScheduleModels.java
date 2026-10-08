package com.stomatologia.client.model;

import java.time.LocalTime;
import java.util.List;

public final class ScheduleModels {

    private ScheduleModels() {
    }

    public record ScheduleDto(Long id, Long doctorId, String doctorName, int dayOfWeek, LocalTime startTime,
                              LocalTime endTime) {
    }

    public record WorkDay(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    public record WeekRequest(List<WorkDay> days) {
    }
}
