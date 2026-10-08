package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.DoctorDtos.SpecialtyDto;
import com.stomatologia.backend.repository.SpecialtyRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/specialties")
public class SpecialtyController {

    private final SpecialtyRepository specialties;

    public SpecialtyController(SpecialtyRepository specialties) {
        this.specialties = specialties;
    }

    @GetMapping
    public List<SpecialtyDto> findAll() {
        return specialties.findAll(Sort.by("name")).stream()
                .map(s -> new SpecialtyDto(s.getId(), s.getName()))
                .toList();
    }
}
