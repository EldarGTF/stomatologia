package com.stomatologia.client.model;

public final class AuthModels {

    private AuthModels() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, UserInfo user) {
    }
}
