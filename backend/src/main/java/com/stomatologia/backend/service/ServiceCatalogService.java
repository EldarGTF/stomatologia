package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.dto.ServiceDtos.ServiceDto;
import com.stomatologia.backend.dto.ServiceDtos.ServiceRequest;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Прейскурант клиники: услуги, их стоимость и длительность.
 */
@Service
public class ServiceCatalogService {

    private final ClinicServiceRepository services;

    public ServiceCatalogService(ClinicServiceRepository services) {
        this.services = services;
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> findAll(boolean onlyActive) {
        List<ClinicService> list = onlyActive ? services.findByActiveTrueOrderByName() : services.findAllByOrderByName();
        return list.stream().map(ServiceDto::from).toList();
    }

    @Transactional
    public ServiceDto create(ServiceRequest request) {
        ensureUniqueName(request.name().trim(), null);
        ClinicService s = new ClinicService();
        apply(s, request);
        return ServiceDto.from(services.save(s));
    }

    @Transactional
    public ServiceDto update(Long id, ServiceRequest request) {
        ClinicService s = find(id);
        ensureUniqueName(request.name().trim(), id);
        apply(s, request);
        return ServiceDto.from(s);
    }

    @Transactional
    public void delete(Long id) {
        ClinicService s = find(id);
        try {
            services.delete(s);
            services.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Услуга используется в приёмах — удалить нельзя. Снимите отметку «Активна».");
        }
    }

    public ClinicService find(Long id) {
        return services.findById(id).orElseThrow(() -> ApiException.notFound("Услуга не найдена"));
    }

    private void ensureUniqueName(String name, Long selfId) {
        services.findByName(name)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw ApiException.conflict("Услуга «" + name + "» уже есть в прейскуранте");
                });
    }

    private static void apply(ClinicService s, ServiceRequest r) {
        s.setName(r.name().trim());
        s.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        s.setPrice(r.price());
        s.setDurationMinutes(r.durationMinutes());
        s.setActive(r.active());
    }
}
