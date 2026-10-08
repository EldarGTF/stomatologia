package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создание и обновление учётных записей для входа в систему (врачи, пациенты).
 */
@Service
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AccountService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * Возвращает учётную запись с учётом запроса: создаёт новую, меняет пароль или оставляет как есть.
     */
    @Transactional
    public User upsert(User existing, String username, String password, String fullName, Role role) {
        String login = blankToNull(username);
        String pass = blankToNull(password);

        if (existing == null) {
            if (login == null) {
                return null;
            }
            if (pass == null) {
                throw ApiException.badRequest("Для новой учётной записи укажите пароль");
            }
            if (users.existsByUsername(login)) {
                throw ApiException.conflict("Логин «" + login + "» уже занят");
            }
            checkPassword(pass);
            return users.save(new User(login, encoder.encode(pass), fullName, role));
        }

        if (login != null && !login.equals(existing.getUsername())) {
            if (users.existsByUsername(login)) {
                throw ApiException.conflict("Логин «" + login + "» уже занят");
            }
            existing.setUsername(login);
        }
        if (pass != null) {
            checkPassword(pass);
            existing.setPasswordHash(encoder.encode(pass));
        }
        existing.setFullName(fullName);
        return existing;
    }

    private static void checkPassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Пароль должен быть не короче " + MIN_PASSWORD_LENGTH + " символов");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
