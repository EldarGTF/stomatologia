package com.stomatologia.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.common.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Защита публичного API от перебора и ботов: отдельные лимиты на чтение и на запись/отмену с одного IP.
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger(PublicRateLimitFilter.class);
    private static final String PREFIX = "/api/public/";

    private static final String CHAT = PREFIX + "chat/";

    private final RateLimiter reads;
    private final RateLimiter writes;
    private final RateLimiter chat;
    private final ObjectMapper mapper;

    public PublicRateLimitFilter(@Value("${app.public-api.reads-per-minute:120}") int readsPerMinute,
                                 @Value("${app.public-api.writes-per-hour:10}") int writesPerHour,
                                 @Value("${app.public-api.chat-messages-per-hour:60}") int chatMessagesPerHour,
                                 ObjectMapper mapper) {
        this.reads = new RateLimiter(readsPerMinute, Duration.ofMinutes(1));
        this.writes = new RateLimiter(writesPerHour, Duration.ofHours(1));
        this.chat = new RateLimiter(chatMessagesPerHour, Duration.ofHours(1));
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        boolean write = "POST".equalsIgnoreCase(request.getMethod());
        boolean chatMessage = write && request.getRequestURI().startsWith(CHAT);
        RateLimiter limiter = chatMessage ? chat : write ? writes : reads;
        if (limiter.tryAcquire(ip)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Публичный API: превышен лимит {} с адреса {} ({} {})",
                chatMessage ? "сообщений чата" : write ? "записей" : "запросов", ip,
                request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limiter.retryAfterSeconds(ip)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(),
                chatMessage ? "Слишком много сообщений. Попробуйте позже или позвоните в клинику"
                        : write ? "Слишком много попыток записи. Попробуйте позже или позвоните в клинику"
                        : "Слишком много запросов. Подождите минуту и обновите страницу"));
    }
}
