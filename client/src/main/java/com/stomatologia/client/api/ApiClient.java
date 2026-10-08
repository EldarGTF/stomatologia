package com.stomatologia.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP-клиент REST API сервера. Добавляет JWT текущей сессии и превращает ошибки сервера в {@link ApiException}.
 */
public final class ApiClient {

    private static final ApiClient INSTANCE = new ApiClient(System.getProperty("api.url", "http://localhost:8080"));

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static ApiClient get() {
        return INSTANCE;
    }

    public <T> T get(String path, Class<T> type) {
        return read(send(builder(path).GET()), type);
    }

    public <T> T get(String path, TypeReference<T> type) {
        return read(send(builder(path).GET()), type);
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return read(send(builder(path).POST(json(body))), type);
    }

    public void post(String path, Object body) {
        send(builder(path).POST(json(body)));
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return read(send(builder(path).PUT(json(body))), type);
    }

    public void delete(String path) {
        send(builder(path).DELETE());
    }

    public byte[] download(String path) {
        return send(builder(path).GET()).body();
    }

    /**
     * Собирает строку запроса, пропуская пустые параметры.
     */
    public static String query(String path, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null && !value.toString().isBlank()) {
                params.put(keyValues[i].toString(), value);
            }
        }
        if (params.isEmpty()) {
            return path;
        }
        return path + "?" + params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private HttpRequest.Builder builder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/octet-stream");
        String token = Session.token();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private HttpRequest.BodyPublisher json(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(
                    body == null ? "" : mapper.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(0, "Не удалось подготовить данные запроса");
        }
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder builder) {
        HttpRequest request = builder.header("Content-Type", "application/json; charset=UTF-8").build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (ConnectException e) {
            throw new ApiException(0, "Сервер недоступен (" + baseUrl + "). Убедитесь, что backend запущен.");
        } catch (IOException e) {
            throw new ApiException(0, "Ошибка связи с сервером: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Запрос прерван");
        }
        if (response.statusCode() >= 400) {
            throw new ApiException(response.statusCode(), errorMessage(response));
        }
        return response;
    }

    private String errorMessage(HttpResponse<byte[]> response) {
        try {
            JsonNode node = mapper.readTree(response.body());
            if (node != null && node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (IOException ignored) {
            // тело ответа не JSON
        }
        return switch (response.statusCode()) {
            case 401 -> "Требуется вход в систему";
            case 403 -> "Недостаточно прав для выполнения действия";
            case 404 -> "Данные не найдены";
            default -> "Ошибка сервера (" + response.statusCode() + ")";
        };
    }

    private <T> T read(HttpResponse<byte[]> response, Class<T> type) {
        if (type == Void.class || response.body().length == 0) {
            return null;
        }
        try {
            return mapper.readValue(response.body(), type);
        } catch (IOException e) {
            throw new ApiException(0, "Не удалось разобрать ответ сервера");
        }
    }

    private <T> T read(HttpResponse<byte[]> response, TypeReference<T> type) {
        if (response.body().length == 0) {
            return null;
        }
        try {
            return mapper.readValue(response.body(), type);
        } catch (IOException e) {
            throw new ApiException(0, "Не удалось разобрать ответ сервера");
        }
    }
}
