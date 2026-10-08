package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.dto.AuthDtos.LoginRequest;
import com.stomatologia.backend.dto.AuthDtos.LoginResponse;
import com.stomatologia.backend.dto.AuthDtos.UserInfo;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import com.stomatologia.backend.security.JwtService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LogManager.getLogger(AuthService.class);

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, DoctorRepository doctors, PatientRepository patients,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = users.findByUsername(username)
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Неудачная попытка входа: логин «{}»", username);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
                });
        if (!user.isActive()) {
            log.warn("Попытка входа в заблокированную учётную запись «{}»", username);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Учётная запись заблокирована");
        }
        log.info("Вход в систему: {} ({}, {})", user.getUsername(), user.getFullName(), user.getRole());
        Long doctorId = doctors.findByUserId(user.getId()).map(d -> d.getId()).orElse(null);
        Long patientId = patients.findByUserId(user.getId()).map(p -> p.getId()).orElse(null);
        AuthUser authUser = new AuthUser(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                doctorId, patientId);
        return new LoginResponse(jwt.generate(authUser), toInfo(authUser));
    }

    public static UserInfo toInfo(AuthUser u) {
        return new UserInfo(u.id(), u.username(), u.fullName(), u.role(), u.doctorId(), u.patientId());
    }
}
