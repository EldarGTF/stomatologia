package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /** Предстоящие запланированные приёмы пациентов из указанного источника — для лимита онлайн-записей. */
    @Query("""
            select count(a) from Appointment a
            where a.patient.id in :patientIds
              and a.source in :sources
              and a.status = com.stomatologia.backend.domain.AppointmentStatus.SCHEDULED
              and a.startAt > :now
            """)
    long countUpcoming(@Param("patientIds") Collection<Long> patientIds,
                       @Param("sources") Collection<AppointmentSource> sources, @Param("now") LocalDateTime now);

    /**
     * Активные (не отменённые) приёмы врача, пересекающиеся с интервалом [start, end).
     */
    @Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId
              and a.status <> com.stomatologia.backend.domain.AppointmentStatus.CANCELLED
              and a.startAt < :end and a.endAt > :start
              and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Appointment> findDoctorOverlaps(@Param("doctorId") Long doctorId, @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    @Query("""
            select a from Appointment a
            where a.room.id = :roomId
              and a.status <> com.stomatologia.backend.domain.AppointmentStatus.CANCELLED
              and a.startAt < :end and a.endAt > :start
              and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Appointment> findRoomOverlaps(@Param("roomId") Long roomId, @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    @Query("""
            select a from Appointment a
            where a.patient.id = :patientId
              and a.status <> com.stomatologia.backend.domain.AppointmentStatus.CANCELLED
              and a.startAt < :end and a.endAt > :start
              and (:excludeId is null or a.id <> :excludeId)
            """)
    List<Appointment> findPatientOverlaps(@Param("patientId") Long patientId, @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    /**
     * Все активные приёмы в интервале — для расчёта свободных окон и загрузки.
     */
    @EntityGraph(attributePaths = {"doctor", "room", "service", "patient"})
    @Query("""
            select a from Appointment a
            where a.status <> com.stomatologia.backend.domain.AppointmentStatus.CANCELLED
              and a.startAt < :to and a.endAt > :from
            order by a.startAt
            """)
    List<Appointment> findActiveBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByStartAtGreaterThanEqualAndStartAtLessThanAndStatus(LocalDateTime from, LocalDateTime to,
                                                                   com.stomatologia.backend.domain.AppointmentStatus status);

    @Override
    @EntityGraph(attributePaths = {"doctor", "doctor.specialty", "room", "service", "patient"})
    List<Appointment> findAll(org.springframework.data.jpa.domain.Specification<Appointment> spec,
                              org.springframework.data.domain.Sort sort);
}
