package com.stomatologia.backend.security;

import com.stomatologia.backend.domain.Role;

/**
 * Данные авторизованного пользователя, извлечённые из JWT.
 */
public record AuthUser(Long id, String username, String fullName, Role role, Long doctorId, Long patientId) {

    public boolean is(Role r) {
        return role == r;
    }
}
