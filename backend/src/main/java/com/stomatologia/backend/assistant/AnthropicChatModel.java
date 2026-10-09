package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude через Messages API Anthropic (https://docs.anthropic.com/en/api/messages) без SDK: один POST-запрос
 * на ход модели. При перегрузке (429, 529, 5xx) делается одна повторная попытка.
 */
@Component
public class AnthropicChatModel implements ChatModel {

    private static final Logger log = LogManager.getLogger(AnthropicChatModel.class);
    static final String API_VERSION = "2023-06-01";

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int maxTokens;
    private final Duration timeout;

    public AnthropicChatModel(ObjectMapper mapper,
                              @Value("${app.assistant.api-key:}") String apiKey,
                              @Value("${app.assistant.model:claude-haiku-4-5}") String model,
                              @Value("${app.assistant.base-url:https://api.anthropic.com}") String baseUrl,
                              @Value("${app.assistant.max-tokens:1024}") int maxTokens,
                              @Value("${app.assistant.timeout-seconds:40}") int timeoutSeconds) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (available()) {
            log.info("ИИ-менеджер: модель {} ({})", model, this.baseUrl);
        } else {
            log.info("ИИ-менеджер выключен: не задан ANTHROPIC_API_KEY, разговоры сразу получает администратор");
        }
    }

    @Override
    public boolean available() {
        return !apiKey.isEmpty();
    }

    @Override
    public ModelReply reply(String system, List<ModelMessage> messages, List<ToolSpec> tools) {
        if (!available()) {
            throw new ChatModelException("Не задан ключ ANTHROPIC_API_KEY");
        }
        String body;
        try {
            body = mapper.writeValueAsString(request(system, messages, tools));
        } catch (IOException e) {
            throw new ChatModelException("Не удалось сформировать запрос к модели", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send(request);
        if (retryable(response.statusCode())) {
            log.warn("Модель ответила {}, повтор через секунду", response.statusCode());
            sleep(1000);
            response = send(request);
        }
        if (response.statusCode() != 200) {
            throw new ChatModelException("Модель вернула " + response.statusCode() + ": " + errorMessage(response.body()));
        }
        try {
            return parse(mapper.readTree(response.body()));
        } catch (IOException e) {
            throw new ChatModelException("Не удалось разобрать ответ модели", e);
        }
    }

    /** Тело запроса. История приводится к требованиям API: начинается с клиента, роли чередуются. */
    ObjectNode request(String system, List<ModelMessage> messages, List<ToolSpec> tools) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("system", system);
        ArrayNode toolArray = root.putArray("tools");
        for (ToolSpec t : tools) {
            ObjectNode node = toolArray.addObject();
            node.put("name", t.name());
            node.put("description", t.description());
            node.set("input_schema", mapper.valueToTree(t.inputSchema()));
        }
        ArrayNode out = root.putArray("messages");
        String lastRole = null;
        ArrayNode lastContent = null;
        for (ModelMessage m : messages) {
            String role = m instanceof AssistantTurn ? "assistant" : "user";
            ArrayNode blocks = blocks(m);
            if (blocks.isEmpty() || (lastRole == null && role.equals("assistant"))) {
                continue;
            }
            if (role.equals(lastRole)) {
                lastContent.addAll(blocks);
            } else {
                ObjectNode msg = out.addObject();
                msg.put("role", role);
                lastContent = msg.putArray("content");
                lastContent.addAll(blocks);
                lastRole = role;
            }
        }
        return root;
    }

    private ArrayNode blocks(ModelMessage m) {
        ArrayNode blocks = mapper.createArrayNode();
        switch (m) {
            case UserText u -> addText(blocks, u.text());
            case AssistantTurn a -> {
                addText(blocks, a.text());
                for (ToolCall call : a.toolCalls()) {
                    ObjectNode use = blocks.addObject();
                    use.put("type", "tool_use");
                    use.put("id", call.id());
                    use.put("name", call.name());
                    use.set("input", call.input() == null ? mapper.createObjectNode() : call.input());
                }
            }
            case ToolResults r -> {
                for (ToolResult result : r.results()) {
                    ObjectNode node = blocks.addObject();
                    node.put("type", "tool_result");
                    node.put("tool_use_id", result.toolCallId());
                    node.put("content", result.content());
                    if (result.error()) {
                        node.put("is_error", true);
                    }
                }
            }
        }
        return blocks;
    }

    private static void addText(ArrayNode blocks, String text) {
        if (text != null && !text.isBlank()) {
            blocks.addObject().put("type", "text").put("text", text);
        }
    }

    ModelReply parse(JsonNode response) {
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode block : response.path("content")) {
            switch (block.path("type").asText()) {
                case "text" -> {
                    if (!text.isEmpty()) {
                        text.append("\n\n");
                    }
                    text.append(block.path("text").asText());
                }
                case "tool_use" -> calls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(),
                        block.path("input")));
                default -> {
                    // другие типы блоков (например, размышления модели) клиенту не показываются
                }
            }
        }
        JsonNode usage = response.path("usage");
        log.debug("Ответ модели: stop_reason={}, токены {}/{}", response.path("stop_reason").asText(),
                usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt());
        return new ModelReply(text.toString().trim(), calls);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ChatModelException("Нет связи с моделью: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatModelException("Запрос к модели прерван", e);
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            return node.path("error").path("message").asText(body);
        } catch (IOException e) {
            return body;
        }
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 529 || status >= 500;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
