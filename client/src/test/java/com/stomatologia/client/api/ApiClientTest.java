package com.stomatologia.client.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ApiClientTest {

    @Test
    void pathWithoutParametersIsUnchanged() {
        assertThat(ApiClient.query("/api/appointments")).isEqualTo("/api/appointments");
        assertThat(ApiClient.query("/api/appointments", "doctorId", null, "status", " ")).isEqualTo("/api/appointments");
    }

    @Test
    void emptyParametersAreSkipped() {
        String path = ApiClient.query("/api/appointments",
                "from", LocalDate.of(2026, 10, 1), "to", null, "doctorId", 3L, "status", "");

        assertThat(path).isEqualTo("/api/appointments?from=2026-10-01&doctorId=3");
    }

    @Test
    void cyrillicAndSpacesAreEncoded() {
        String path = ApiClient.query("/api/patients", "search", "Алексеев Игорь");

        assertThat(path).startsWith("/api/patients?search=")
                .doesNotContain(" ")
                .contains("%D0%90");
    }
}
