package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.dto.PatientDtos.PatientDto;
import com.stomatologia.backend.dto.PatientDtos.PatientRequest;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientService {

    private final PatientRepository patients;
    private final UserRepository users;
    private final AccountService accounts;

    public PatientService(PatientRepository patients, UserRepository users, AccountService accounts) {
        this.patients = patients;
        this.users = users;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<PatientDto> search(String query) {
        String q = query == null ? "" : query.trim();
        return patients.search(q).stream().map(PatientDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PatientDto get(Long id) {
        return PatientDto.from(find(id));
    }

    @Transactional(readOnly = true)
    public PatientDto getByUser(Long userId) {
        return patients.findByUserId(userId).map(PatientDto::from)
                .orElseThrow(() -> ApiException.notFound("Карточка пациента не найдена"));
    }

    @Transactional
    public PatientDto create(PatientRequest request) {
        Patient p = new Patient();
        apply(p, request);
        return PatientDto.from(patients.save(p));
    }

    @Transactional
    public PatientDto update(Long id, PatientRequest request) {
        Patient p = find(id);
        apply(p, request);
        return PatientDto.from(p);
    }

    @Transactional
    public void delete(Long id) {
        Patient p = find(id);
        User account = p.getUser();
        try {
            patients.delete(p);
            patients.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("У пациента есть приёмы или счета — удалить карточку нельзя");
        }
        if (account != null) {
            users.delete(account);
        }
    }

    Patient find(Long id) {
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("Пациент не найден"));
    }

    private void apply(Patient p, PatientRequest r) {
        p.setLastName(r.lastName().trim());
        p.setFirstName(r.firstName().trim());
        p.setMiddleName(trimToNull(r.middleName()));
        p.setBirthDate(r.birthDate());
        p.setPhone(trimToNull(r.phone()));
        p.setEmail(trimToNull(r.email()));
        p.setAddress(trimToNull(r.address()));
        p.setNotes(trimToNull(r.notes()));
        p.setUser(accounts.upsert(p.getUser(), r.username(), r.password(), p.getFullName(), Role.PATIENT));
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
