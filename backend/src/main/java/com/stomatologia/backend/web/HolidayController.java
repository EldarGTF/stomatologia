package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.SettingsDtos.HolidayDto;
import com.stomatologia.backend.dto.SettingsDtos.HolidayRequest;
import com.stomatologia.backend.service.ClinicSettingsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Нерабочие дни клиники. Без параметров — текущий и следующий год.
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final ClinicSettingsService settings;

    public HolidayController(ClinicSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public List<HolidayDto> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : start.plusYears(2).minusDays(1);
        return settings.holidays(start, end);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public HolidayDto create(@Valid @RequestBody HolidayRequest request) {
        return settings.addHoliday(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        settings.deleteHoliday(id);
    }
}
