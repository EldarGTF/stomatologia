package com.stomatologia.client.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SettingsModels {

    private SettingsModels() {
    }

    public record SettingsDto(String name, String address, String phone, String email,
                              String bin, String bankName, String iik, String bik,
                              int slotStepMinutes, int bookingHorizonDays, int minLeadHours, int patientCancelHours,
                              String invoicePrefix, boolean vatEnabled, BigDecimal vatRate,
                              BigDecimal prepaymentThreshold, LocalDateTime updatedAt, String updatedBy) {
    }

    public record SettingsRequest(String name, String address, String phone, String email,
                                  String bin, String bankName, String iik, String bik,
                                  int slotStepMinutes, int bookingHorizonDays, int minLeadHours,
                                  int patientCancelHours, String invoicePrefix, boolean vatEnabled,
                                  BigDecimal vatRate, BigDecimal prepaymentThreshold) {
    }

    public record HolidayDto(Long id, LocalDate day, String name, int scheduledAppointments) {
    }

    public record HolidayRequest(LocalDate day, String name) {
    }
}
