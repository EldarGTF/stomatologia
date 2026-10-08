package com.stomatologia.backend.web;

import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentDto;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.dto.AppointmentDtos.AuditDto;
import com.stomatologia.backend.dto.AppointmentDtos.CancelRequest;
import com.stomatologia.backend.dto.AppointmentDtos.StatusRequest;
import com.stomatologia.backend.service.AppointmentService;
import com.stomatologia.backend.service.AppointmentService.Filter;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<AppointmentDto> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.search(new Filter(from, to, doctorId, patientId, status));
    }

    @GetMapping("/{id}")
    public AppointmentDto get(@PathVariable Long id) {
        return appointmentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR', 'PATIENT')")
    public AppointmentDto create(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR', 'PATIENT')")
    public AppointmentDto update(@PathVariable Long id, @Valid @RequestBody AppointmentRequest request) {
        return appointmentService.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR', 'PATIENT')")
    public AppointmentDto cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request) {
        return appointmentService.cancel(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRAR', 'DOCTOR')")
    public AppointmentDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return appointmentService.changeStatus(id, request.status());
    }

    @GetMapping("/{id}/audit")
    public List<AuditDto> history(@PathVariable Long id) {
        return appointmentService.history(id);
    }
}
