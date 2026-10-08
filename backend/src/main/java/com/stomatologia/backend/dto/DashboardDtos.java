package com.stomatologia.backend.dto;

import com.stomatologia.backend.dto.AppointmentDtos.AppointmentDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** holidayName — название праздника, если сегодня нерабочий день клиники, иначе null. */
    public record DashboardDto(LocalDate date, int appointmentsToday, int scheduled, int completed, int noShow,
                               int cancelled, int awaitingMark, int freeWindows, int windowMinutes, BigDecimal revenueToday,
                               BigDecimal revenueMonth, BigDecimal outstanding, List<DayRevenue> revenueByDay,
                               List<DoctorLoad> doctorLoad, List<AppointmentDto> upcoming, String holidayName) {
    }

    public record DayRevenue(LocalDate date, BigDecimal amount) {
    }

    /**
     * Загрузка врача за день: доля рабочего времени по графику, занятая приёмами (кроме отменённых).
     */
    public record DoctorLoad(Long doctorId, String doctorName, String specialtyName, String roomNumber,
                             boolean working, int workMinutes, int bookedMinutes, int appointments,
                             int freeWindows, int loadPercent) {
    }
}
