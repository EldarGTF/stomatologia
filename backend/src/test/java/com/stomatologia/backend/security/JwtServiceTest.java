package com.stomatologia.backend.security;

import com.stomatologia.backend.domain.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService service = new JwtService("test-secret-key-0123456789-0123456789-abcdef", 1);

    @Test
    void tokenRoundTripKeepsUserData() {
        AuthUser user = new AuthUser(7L, "ivanova", "Иванова Елена Петровна", Role.DOCTOR, 3L, null);

        AuthUser parsed = service.parse(service.generate(user));

        assertThat(parsed).isEqualTo(user);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.generate(new AuthUser(1L, "admin", "Админ", Role.ADMIN, null, null));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsNotAllowed() {
        assertThatThrownBy(() -> new JwtService("short", 1)).isInstanceOf(IllegalStateException.class);
    }
}
