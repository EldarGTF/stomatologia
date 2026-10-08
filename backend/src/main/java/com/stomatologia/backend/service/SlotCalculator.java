package com.stomatologia.backend.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * Сколько минут рабочего времени занято интервалами (части интервалов вне рабочего времени не считаются,
     * пересекающиеся интервалы не учитываются дважды).
     */
    public static int busyMinutes(LocalDate date, LocalTime workStart, LocalTime workEnd, List<Interval> busy) {
        LocalDateTime dayStart = date.atTime(workStart);
        LocalDateTime dayEnd = date.atTime(workEnd);
        List<Interval> clipped = busy.stream()
                .filter(b -> b.overlaps(dayStart, dayEnd))
                .map(b -> new Interval(b.start().isBefore(dayStart) ? dayStart : b.start(),
                        b.end().isAfter(dayEnd) ? dayEnd : b.end()))
                .sorted(Comparator.comparing(Interval::start))
                .toList();
        long minutes = 0;
        LocalDateTime coveredUntil = dayStart;
        for (Interval b : clipped) {
            LocalDateTime from = b.start().isAfter(coveredUntil) ? b.start() : coveredUntil;
            if (b.end().isAfter(from)) {
                minutes += Duration.between(from, b.end()).toMinutes();
                coveredUntil = b.end();
            }
        }
        return (int) minutes;
    }
}
