package com.stomatologia.client.api;

import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.UserInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Текущая сессия пользователя: JWT и сведения о вошедшем пользователе. Хранится только в памяти.
 */
public final class Session {

    private static final Logger log = LogManager.getLogger(Session.class);

    private static String token;
    private static UserInfo user;

    private Session() {
    }

    public static void start(String jwt, UserInfo info) {
        token = jwt;
        user = info;
        log.info("Вход выполнен: {} ({}, {})", info.username(), info.fullName(), info.role());
    }

    public static void clear() {
        if (user != null) {
            log.info("Выход из системы: {}", user.username());
        }
        token = null;
        user = null;
    }

    public static String token() {
        return token;
    }

    public static UserInfo user() {
        return user;
    }

    public static Role role() {
        return user == null ? null : user.role();
    }

    public static boolean hasRole(Role... roles) {
        Role current = role();
        for (Role r : roles) {
            if (r == current) {
                return true;
            }
        }
        return false;
    }
}
