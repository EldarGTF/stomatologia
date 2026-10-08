package com.stomatologia.backend.dto;

import com.stomatologia.backend.domain.Role;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Введите логин") String username,
            @NotBlank(message = "Введите пароль") String password) {
    }

    public record UserInfo(Long id, String username, String fullName, Role role, Long doctorId, Long patientId) {
    }

    public record LoginResponse(String token, UserInfo user) {
    }
}
