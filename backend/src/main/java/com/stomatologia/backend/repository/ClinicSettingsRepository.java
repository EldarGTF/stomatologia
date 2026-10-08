package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.ClinicSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, Short> {
}
