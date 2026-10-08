package com.stomatologia.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Расчёт свободных временных окон внутри рабочего дня с учётом уже занятых интервалов.
 */
public final class SlotCalculator {

    private SlotCalculator() {
    }

    public record Interval(LocalDateTime start, LocalDateTime end) {

        public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
            return start.isBefore(otherEnd) && end.isAfter(otherStart);
        }
    }

    /**
     * Возвращает начала всех окон длиной {@code durationMinutes}, которые помещаются в рабочее время,
     * не пересекаются с занятыми интервалами и начинаются не раньше {@code notBefore}.
     */
    public static List<LocalDateTime> freeStarts(LocalDate date, LocalTime workStart, LocalTime workEnd,
                                                 int durationMinutes, int stepMinutes, List<Interval> busy,
                                                 LocalDateTime notBefore) {
        List<LocalDateTime> result = new ArrayList<>();
        LocalDateTime dayEnd = date.atTime(workEnd);
        for (LocalDateTime start = date.atTime(workStart);
             !start.plusMinutes(durationMinutes).isAfter(dayEnd);
             start = start.plusMinutes(stepMinutes)) {
            if (notBefore != null && start.isBefore(notBefore)) {
                continue;
            }
            LocalDateTime end = start.plusMinutes(durationMinutes);
            LocalDateTime s = start;
            if (busy.stream().noneMatch(b -> b.overlaps(s, end))) {
                result.add(start);
            }
        }
        return result;
    }
}
