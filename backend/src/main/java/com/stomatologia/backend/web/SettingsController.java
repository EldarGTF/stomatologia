package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.SettingsDtos.SettingsDto;
import com.stomatologia.backend.dto.SettingsDtos.SettingsRequest;
import com.stomatologia.backend.service.ClinicSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Настройки клиники: читать могут все (клиенту нужны правила записи), менять — только администратор.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final ClinicSettingsService settings;

    public SettingsController(ClinicSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsDto get() {
        return settings.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto update(@Valid @RequestBody SettingsRequest request) {
        return settings.update(request);
    }
}
