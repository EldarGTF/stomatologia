package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.ServiceDtos.ServiceDto;
import com.stomatologia.backend.dto.ServiceDtos.ServiceRequest;
import com.stomatologia.backend.service.ServiceCatalogService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceCatalogService catalog;

    public ServiceController(ServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ServiceDto> findAll(@RequestParam(defaultValue = "false") boolean active) {
        return catalog.findAll(active);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceDto create(@Valid @RequestBody ServiceRequest request) {
        return catalog.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceDto update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return catalog.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        catalog.delete(id);
    }
}
