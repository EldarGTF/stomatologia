package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.ScheduleDtos.ScheduleDto;
import com.stomatologia.backend.dto.ScheduleDtos.WeekRequest;
import com.stomatologia.backend.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<ScheduleDto> find(@RequestParam(required = false) Long doctorId) {
        return scheduleService.find(doctorId);
    }

    @PutMapping("/doctor/{doctorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ScheduleDto> replaceWeek(@PathVariable Long doctorId, @Valid @RequestBody WeekRequest request) {
        return scheduleService.replaceWeek(doctorId, request);
    }
}
