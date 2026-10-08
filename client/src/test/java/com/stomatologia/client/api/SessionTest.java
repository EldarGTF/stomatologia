package com.stomatologia.client.api;

import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @AfterEach
    void logout() {
        Session.clear();
    }

    @Test
    void sessionKeepsTokenAndRole() {
        Session.start("jwt-token", new UserInfo(5L, "ivanova", "Иванова Елена Петровна", Role.DOCTOR, 1L, null));

        assertThat(Session.token()).isEqualTo("jwt-token");
        assertThat(Session.role()).isEqualTo(Role.DOCTOR);
        assertThat(Session.hasRole(Role.ADMIN, Role.DOCTOR)).isTrue();
        assertThat(Session.hasRole(Role.PATIENT)).isFalse();
    }

    @Test
    void logoutForgetsUser() {
        Session.start("jwt-token", new UserInfo(1L, "admin", "Администратор системы", Role.ADMIN, null, null));

        Session.clear();

        assertThat(Session.token()).isNull();
        assertThat(Session.user()).isNull();
        assertThat(Session.hasRole(Role.ADMIN)).isFalse();
    }
}
