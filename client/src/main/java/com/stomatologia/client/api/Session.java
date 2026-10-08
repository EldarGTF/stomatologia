package com.stomatologia.client.api;

import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.UserInfo;

/**
 * Текущая сессия пользователя: JWT и сведения о вошедшем пользователе. Хранится только в памяти.
 */
public final class Session {

    private static String token;
    private static UserInfo user;

    private Session() {
    }

    public static void start(String jwt, UserInfo info) {
        token = jwt;
        user = info;
    }

    public static void clear() {
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
