package com.stomatologia.backend.security;

import com.stomatologia.backend.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-hours}") long expirationHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET должен быть не короче 32 байт");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(expirationHours);
    }

    public String generate(AuthUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.username())
                .claim("uid", user.id())
                .claim("name", user.fullName())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (user.doctorId() != null) {
            builder.claim("doctorId", user.doctorId());
        }
        if (user.patientId() != null) {
            builder.claim("patientId", user.patientId());
        }
        return builder.signWith(key).compact();
    }

    public AuthUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new AuthUser(
                toLong(c.get("uid")),
                c.getSubject(),
                c.get("name", String.class),
                Role.valueOf(c.get("role", String.class)),
                toLong(c.get("doctorId")),
                toLong(c.get("patientId")));
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
