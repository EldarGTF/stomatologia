package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Room;
import com.stomatologia.backend.domain.Specialty;
import com.stomatologia.backend.dto.DoctorDtos.SpecialtyDto;
import com.stomatologia.backend.dto.DoctorDtos.SpecialtyRequest;
import com.stomatologia.backend.dto.RoomDtos.RoomDto;
import com.stomatologia.backend.dto.RoomDtos.RoomRequest;
import com.stomatologia.backend.repository.RoomRepository;
import com.stomatologia.backend.repository.SpecialtyRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Небольшие справочники: кабинеты и специальности врачей.
 */
@Service
public class ReferenceService {

    private static final Logger log = LogManager.getLogger(ReferenceService.class);

    private final RoomRepository rooms;
    private final SpecialtyRepository specialties;

    public ReferenceService(RoomRepository rooms, SpecialtyRepository specialties) {
        this.rooms = rooms;
        this.specialties = specialties;
    }

    @Transactional(readOnly = true)
    public List<RoomDto> rooms() {
        return rooms.findAll(Sort.by("number")).stream().map(RoomDto::from).toList();
    }

    @Transactional
    public RoomDto saveRoom(Long id, RoomRequest request) {
        String number = request.number().trim();
        rooms.findByNumber(number)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Кабинет № " + number + " уже существует");
                });
        Room room = id == null ? new Room()
                : rooms.findById(id).orElseThrow(() -> ApiException.notFound("Кабинет не найден"));
        room.setNumber(number);
        room.setName(request.name().trim());
        room = rooms.save(room);
        log.info("Кабинет № {} «{}» {}", room.getNumber(), room.getName(), id == null ? "добавлен" : "изменён");
        return RoomDto.from(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
        Room room = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Кабинет не найден"));
        try {
            rooms.delete(room);
            rooms.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Кабинет № " + room.getNumber()
                    + " закреплён за врачом или используется в приёмах — удалить нельзя");
        }
        log.info("Удалён кабинет № {} «{}»", room.getNumber(), room.getName());
    }

    @Transactional(readOnly = true)
    public List<SpecialtyDto> specialties() {
        return specialties.findAll(Sort.by("name")).stream()
                .map(s -> new SpecialtyDto(s.getId(), s.getName()))
                .toList();
    }

    @Transactional
    public SpecialtyDto saveSpecialty(Long id, SpecialtyRequest request) {
        String name = request.name().trim();
        specialties.findByName(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Специальность «" + name + "» уже существует");
                });
        Specialty s = id == null ? new Specialty()
                : specialties.findById(id).orElseThrow(() -> ApiException.notFound("Специальность не найдена"));
        s.setName(name);
        s = specialties.save(s);
        log.info("Специальность «{}» {}", s.getName(), id == null ? "добавлена" : "изменена");
        return new SpecialtyDto(s.getId(), s.getName());
    }

    @Transactional
    public void deleteSpecialty(Long id) {
        Specialty s = specialties.findById(id).orElseThrow(() -> ApiException.notFound("Специальность не найдена"));
        try {
            specialties.delete(s);
            specialties.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Есть врачи со специальностью «" + s.getName() + "» — удалить нельзя");
        }
        log.info("Удалена специальность «{}»", s.getName());
    }
}
