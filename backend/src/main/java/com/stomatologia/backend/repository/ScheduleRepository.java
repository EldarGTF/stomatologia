package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @Query("select s from Schedule s join fetch s.doctor d order by d.fullName, s.dayOfWeek")
    List<Schedule> findAllWithDoctor();

    List<Schedule> findByDoctorIdOrderByDayOfWeek(Long doctorId);

    Optional<Schedule> findByDoctorIdAndDayOfWeek(Long doctorId, Integer dayOfWeek);

    void deleteByDoctorId(Long doctorId);
}
