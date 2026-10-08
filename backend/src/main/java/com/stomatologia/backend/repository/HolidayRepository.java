package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByDay(LocalDate day);

    List<Holiday> findByDayBetweenOrderByDay(LocalDate from, LocalDate to);

    List<Holiday> findAllByOrderByDay();
}
