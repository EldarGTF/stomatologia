package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.ClinicService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, Long> {

    List<ClinicService> findAllByOrderByName();

    List<ClinicService> findByActiveTrueOrderByName();

    Optional<ClinicService> findByName(String name);
}
