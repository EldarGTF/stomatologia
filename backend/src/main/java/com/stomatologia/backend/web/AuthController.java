package com.stomatologia.backend.web;

import com.stomatologia.backend.dto.AuthDtos.LoginRequest;
import com.stomatologia.backend.dto.AuthDtos.LoginResponse;
import com.stomatologia.backend.dto.AuthDtos.UserInfo;
import com.stomatologia.backend.security.CurrentUser;
import com.stomatologia.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserInfo me() {
        return AuthService.toInfo(CurrentUser.get());
    }
}
