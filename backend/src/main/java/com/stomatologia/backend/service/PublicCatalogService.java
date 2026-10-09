package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.dto.PublicDtos.ClinicInfo;
import com.stomatologia.backend.dto.PublicDtos.DoctorInfo;
import com.stomatologia.backend.dto.PublicDtos.HolidayInfo;
import com.stomatologia.backend.dto.PublicDtos.ServiceInfo;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.service.SlotService.DayAvailability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Справочники и свободное время для сайта: только активные услуги и врачи, которые ведут приём.
 * Окна считаются как для пациента — не раньше минимального времени до приёма из «Настроек».
 */
@Service
public class PublicCatalogService {

    private final ClinicSettingsService clinic;
    private final ClinicServiceRepository services;
    private final DoctorRepository doctors;
    private final SlotService slots;

    public PublicCatalogService(ClinicSettingsService clinic, ClinicServiceRepository services,
                                DoctorRepository doctors, SlotService slots) {
        this.clinic = clinic;
        this.services = services;
        this.doctors = doctors;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public ClinicInfo clinic() {
        return ClinicInfo.from(clinic.current(), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ServiceInfo> services() {
        return services.findByActiveTrueOrderByName().stream().map(ServiceInfo::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorInfo> doctors() {
        return doctors.findAllWithDetails().stream()
                .filter(d -> d.isActive() && d.getRoom() != null)
                .map(DoctorInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HolidayInfo> holidays(LocalDate from, LocalDate to) {
        return clinic.holidays(from, to).stream().map(h -> new HolidayInfo(h.day(), h.name())).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotDto> slots(Long serviceId, Long doctorId, LocalDate date) {
        check(serviceId, doctorId);
        return slots.freeSlots(doctorId, serviceId, date, true);
    }

    @Transactional(readOnly = true)
    public List<DayAvailability> availability(Long serviceId, Long doctorId, LocalDate from, LocalDate to) {
        check(serviceId, doctorId);
        return slots.availability(serviceId, doctorId, from, to, true);
    }

    @Transactional(readOnly = true)
    public SlotDto nearest(Long serviceId, Long doctorId) {
        check(serviceId, doctorId);
        return slots.nearest(serviceId, doctorId, LocalDateTime.now(), true);
    }

    /** Сайт не должен видеть выключенные услуги и врачей, которые не ведут приём. */
    private void check(Long serviceId, Long doctorId) {
        services.findById(serviceId).filter(s -> s.isActive())
                .orElseThrow(() -> ApiException.notFound("Услуга недоступна для онлайн-записи"));
        if (doctorId != null) {
            doctors.findById(doctorId).filter(d -> d.isActive() && d.getRoom() != null)
                    .orElseThrow(() -> ApiException.notFound("Врач не ведёт онлайн-запись"));
        }
    }
}
