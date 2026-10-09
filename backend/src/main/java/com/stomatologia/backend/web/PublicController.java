package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.dto.PublicDtos.BookingInfo;
import com.stomatologia.backend.dto.PublicDtos.BookingRequest;
import com.stomatologia.backend.dto.PublicDtos.ClinicInfo;
import com.stomatologia.backend.dto.PublicDtos.DoctorInfo;
import com.stomatologia.backend.dto.PublicDtos.HolidayInfo;
import com.stomatologia.backend.dto.PublicDtos.ServiceInfo;
import com.stomatologia.backend.service.PublicBookingService;
import com.stomatologia.backend.service.PublicCatalogService;
import com.stomatologia.backend.service.SlotService.DayAvailability;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Публичный API сайта онлайн-записи — без входа в систему. Частота запросов ограничена {@link PublicRateLimitFilter}.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final PublicCatalogService catalog;
    private final PublicBookingService booking;

    public PublicController(PublicCatalogService catalog, PublicBookingService booking) {
        this.catalog = catalog;
        this.booking = booking;
    }

    @GetMapping("/clinic")
    public ClinicInfo clinic() {
        return catalog.clinic();
    }

    @GetMapping("/services")
    public List<ServiceInfo> services() {
        return catalog.services();
    }

    @GetMapping("/doctors")
    public List<DoctorInfo> doctors() {
        return catalog.doctors();
    }

    @GetMapping("/holidays")
    public List<HolidayInfo> holidays(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return catalog.holidays(from, to);
    }

    @GetMapping("/slots")
    public List<SlotDto> slots(@RequestParam Long serviceId, @RequestParam(required = false) Long doctorId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return catalog.slots(serviceId, doctorId, date);
    }

    @GetMapping("/slots/days")
    public List<DayAvailability> days(@RequestParam Long serviceId, @RequestParam(required = false) Long doctorId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return catalog.availability(serviceId, doctorId, from, to);
    }

    @GetMapping("/slots/nearest")
    public SlotDto nearest(@RequestParam Long serviceId, @RequestParam(required = false) Long doctorId) {
        return catalog.nearest(serviceId, doctorId);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingInfo book(@Valid @RequestBody BookingRequest request) {
        return booking.book(request);
    }

    @GetMapping("/bookings/{token}")
    public BookingInfo booking(@PathVariable String token) {
        return booking.get(token);
    }

    @GetMapping("/bookings/{token}/ticket")
    public ResponseEntity<byte[]> ticket(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Талон_на_приём.docx", StandardCharsets.UTF_8).build().toString())
                .body(booking.ticket(token));
    }

    @PostMapping("/bookings/{token}/cancel")
    public BookingInfo cancel(@PathVariable String token) {
        return booking.cancel(token);
    }
}
