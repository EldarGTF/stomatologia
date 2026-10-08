package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.service.SlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public List<SlotDto> freeSlots(@RequestParam Long doctorId, @RequestParam Long serviceId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slotService.freeSlots(doctorId, serviceId, date);
    }

    @GetMapping("/nearest")
    public SlotDto nearest(@RequestParam Long serviceId,
                           @RequestParam(required = false) Long doctorId,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from) {
        return slotService.nearest(serviceId, doctorId, from);
    }
}
