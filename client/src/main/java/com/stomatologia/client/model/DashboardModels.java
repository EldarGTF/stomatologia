package com.stomatologia.client.model;

import com.stomatologia.client.model.AppointmentModels.AppointmentDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardModels {

    private DashboardModels() {
    }

    public record DashboardDto(LocalDate date, int appointmentsToday, int scheduled, int completed, int noShow,
                               int cancelled, int awaitingMark, int freeWindows, int windowMinutes,
                               BigDecimal revenueToday, BigDecimal revenueMonth, BigDecimal outstanding,
                               List<DayRevenue> revenueByDay, List<DoctorLoad> doctorLoad,
                               List<AppointmentDto> upcoming, String holidayName, LeadStats leads) {
    }

    public record LeadStats(int newToday, int open, int conversionPercent) {
    }

    public record DayRevenue(LocalDate date, BigDecimal amount) {
    }

    public record DoctorLoad(Long doctorId, String doctorName, String specialtyName, String roomNumber,
                             boolean working, int workMinutes, int bookedMinutes, int appointments,
                             int freeWindows, int loadPercent) {
    }
}
