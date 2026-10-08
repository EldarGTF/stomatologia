package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.dto.ScheduleDtos.ScheduleDto;
import com.stomatologia.backend.dto.ScheduleDtos.WeekRequest;
import com.stomatologia.backend.dto.ScheduleDtos.WorkDay;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ScheduleService {

    private final ScheduleRepository schedules;
    private final DoctorRepository doctors;

    public ScheduleService(ScheduleRepository schedules, DoctorRepository doctors) {
        this.schedules = schedules;
        this.doctors = doctors;
    }

    @Transactional(readOnly = true)
    public List<ScheduleDto> find(Long doctorId) {
        List<Schedule> list = doctorId == null
                ? schedules.findAllWithDoctor()
                : schedules.findByDoctorIdOrderByDayOfWeek(doctorId);
        return list.stream().map(ScheduleDto::from).toList();
    }

    /**
     * Заменяет рабочую неделю врача целиком.
     */
    @Transactional
    public List<ScheduleDto> replaceWeek(Long doctorId, WeekRequest request) {
        Doctor doctor = doctors.findById(doctorId).orElseThrow(() -> ApiException.notFound("Врач не найден"));
        Set<Integer> seen = new HashSet<>();
        for (WorkDay day : request.days()) {
            if (!seen.add(day.dayOfWeek())) {
                throw ApiException.badRequest("День недели указан дважды");
            }
            if (!day.endTime().isAfter(day.startTime())) {
                throw ApiException.badRequest("Окончание работы должно быть позже начала");
            }
        }
        schedules.deleteByDoctorId(doctorId);
        schedules.flush();
        for (WorkDay day : request.days()) {
            Schedule s = new Schedule();
            s.setDoctor(doctor);
            s.setDayOfWeek(day.dayOfWeek());
            s.setStartTime(day.startTime());
            s.setEndTime(day.endTime());
            schedules.save(s);
        }
        return find(doctorId);
    }
}
