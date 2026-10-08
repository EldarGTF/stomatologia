package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.DoctorDtos.SpecialtyDto;
import com.stomatologia.backend.dto.DoctorDtos.SpecialtyRequest;
import com.stomatologia.backend.service.ReferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/specialties")
public class SpecialtyController {

    private final ReferenceService references;

    public SpecialtyController(ReferenceService references) {
        this.references = references;
    }

    @GetMapping
    public List<SpecialtyDto> findAll() {
        return references.specialties();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SpecialtyDto create(@Valid @RequestBody SpecialtyRequest request) {
        return references.saveSpecialty(null, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SpecialtyDto update(@PathVariable Long id, @Valid @RequestBody SpecialtyRequest request) {
        return references.saveSpecialty(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        references.deleteSpecialty(id);
    }
}
