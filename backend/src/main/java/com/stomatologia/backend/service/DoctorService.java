package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.dto.DoctorDtos.DoctorDto;
import com.stomatologia.backend.dto.DoctorDtos.DoctorRequest;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.RoomRepository;
import com.stomatologia.backend.repository.SpecialtyRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DoctorService {

    private final DoctorRepository doctors;
    private final SpecialtyRepository specialties;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final AccountService accounts;

    public DoctorService(DoctorRepository doctors, SpecialtyRepository specialties, RoomRepository rooms,
                         UserRepository users, AccountService accounts) {
        this.doctors = doctors;
        this.specialties = specialties;
        this.rooms = rooms;
        this.users = users;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<DoctorDto> findAll() {
        return doctors.findAllWithDetails().stream().map(DoctorDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DoctorDto get(Long id) {
        return DoctorDto.from(find(id));
    }

    @Transactional
    public DoctorDto create(DoctorRequest request) {
        Doctor d = new Doctor();
        apply(d, request);
        return DoctorDto.from(doctors.save(d));
    }

    @Transactional
    public DoctorDto update(Long id, DoctorRequest request) {
        Doctor d = find(id);
        apply(d, request);
        return DoctorDto.from(d);
    }

    @Transactional
    public void delete(Long id) {
        Doctor d = find(id);
        User account = d.getUser();
        try {
            doctors.delete(d);
            doctors.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("У врача есть приёмы — удалить нельзя. Снимите отметку «Работает».");
        }
        if (account != null) {
            users.delete(account);
        }
    }

    Doctor find(Long id) {
        return doctors.findById(id).orElseThrow(() -> ApiException.notFound("Врач не найден"));
    }

    private void apply(Doctor d, DoctorRequest r) {
        d.setFullName(r.fullName().trim());
        d.setSpecialty(specialties.findById(r.specialtyId())
                .orElseThrow(() -> ApiException.badRequest("Специальность не найдена")));
        d.setRoom(r.roomId() == null ? null : rooms.findById(r.roomId())
                .orElseThrow(() -> ApiException.badRequest("Кабинет не найден")));
        d.setPhone(trimToNull(r.phone()));
        d.setEmail(trimToNull(r.email()));
        d.setActive(r.active());
        d.setUser(accounts.upsert(d.getUser(), r.username(), r.password(), d.getFullName(), Role.DOCTOR));
        if (d.getUser() != null) {
            d.getUser().setActive(r.active());
        }
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
