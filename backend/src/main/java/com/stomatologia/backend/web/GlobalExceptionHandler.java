package com.stomatologia.backend.web;

import com.stomatologia.backend.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        Level level = ex.getStatus() == HttpStatus.FORBIDDEN || ex.getStatus() == HttpStatus.UNAUTHORIZED
                ? Level.WARN : Level.INFO;
        log.log(level, "{} {} -> {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getStatus().value(), ex.getMessage());
        return error(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("\n"));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "Некорректные данные" : message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Некорректный формат данных запроса");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("{} {} -> 403: доступ запрещён ролью", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.FORBIDDEN, "Недостаточно прав для выполнения действия");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        String sqlState = findSqlState(ex);
        String details = String.valueOf(ex.getMostSpecificCause().getMessage());
        if ("23P01".equals(sqlState)) {
            log.warn("Двойная запись отклонена ограничением БД: {}", details);
            String who = details.contains("no_room_overlap") ? "Кабинет" : "Врач";
            return error(HttpStatus.CONFLICT, who + " уже занят в выбранное время");
        }
        if ("23503".equals(sqlState)) {
            return error(HttpStatus.CONFLICT, "Запись используется в других данных и не может быть удалена");
        }
        if ("23505".equals(sqlState)) {
            return error(HttpStatus.CONFLICT, "Запись с такими данными уже существует");
        }
        log.warn("Нарушение целостности данных: {}", details);
        return error(HttpStatus.CONFLICT, "Нарушение целостности данных");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return error(status, "Некорректный запрос: " + status.getReasonPhrase());
            }
        }
        log.error("Необработанная ошибка", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    private static String findSqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }
}
