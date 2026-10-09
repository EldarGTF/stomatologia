package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ChatModel.AssistantTurn;
import com.stomatologia.backend.assistant.ChatModel.ModelReply;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolResults;
import com.stomatologia.backend.assistant.ChatModel.ToolSpec;
import com.stomatologia.backend.assistant.ChatModel.UserText;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicChatModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AnthropicChatModel model(String key, String baseUrl) {
        return new AnthropicChatModel(mapper, key, "claude-haiku-4-5", baseUrl, 512, 5);
    }

    private static final List<ToolSpec> TOOLS = List.of(new ToolSpec("list_services", "Услуги",
            Map.of("type", "object", "properties", Map.of())));

    @Test
    void withoutKeyModelIsUnavailable() {
        AnthropicChatModel m = model("  ", "http://localhost");

        assertThat(m.available()).isFalse();
        assertThatThrownBy(() -> m.reply("system", List.of(new UserText("Привет")), TOOLS))
                .isInstanceOf(ChatModelException.class);
    }

    @Test
    void historyStartsWithClientAndRolesAlternate() {
        JsonNode body = model("key", "http://localhost").request("Ты администратор", List.of(
                AssistantTurn.text("Здравствуйте! Чем помочь?"),
                new UserText("Сколько стоит чистка?"),
                new UserText("И отбеливание"),
                AssistantTurn.text(""),
                AssistantTurn.text("Чистка — 15 000 ₸")), TOOLS);

        assertThat(body.path("model").asText()).isEqualTo("claude-haiku-4-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(512);
        assertThat(body.path("system").asText()).isEqualTo("Ты администратор");
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("list_services");
        assertThat(body.path("tools").get(0).path("input_schema").path("type").asText()).isEqualTo("object");

        JsonNode messages = body.path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).path("content")).hasSize(2);
        assertThat(messages.get(0).path("content").get(1).path("text").asText()).isEqualTo("И отбеливание");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(1).path("content")).hasSize(1);
    }

    @Test
    void toolCallsAndResultsBecomeBlocks() throws IOException {
        ToolCall call = new ToolCall("toolu_1", "find_free_slots", mapper.readTree("{\"service_id\":5}"));
        JsonNode messages = model("key", "http://localhost").request("s", List.of(
                new UserText("Запишите на чистку"),
                new AssistantTurn("Сейчас посмотрю", List.of(call)),
                new ToolResults(List.of(new ToolResult("toolu_1", "Услуга недоступна", true)))), TOOLS)
                .path("messages");

        JsonNode assistant = messages.get(1).path("content");
        assertThat(assistant.get(0).path("type").asText()).isEqualTo("text");
        assertThat(assistant.get(1).path("type").asText()).isEqualTo("tool_use");
        assertThat(assistant.get(1).path("id").asText()).isEqualTo("toolu_1");
        assertThat(assistant.get(1).path("input").path("service_id").asInt()).isEqualTo(5);

        JsonNode result = messages.get(2).path("content").get(0);
        assertThat(messages.get(2).path("role").asText()).isEqualTo("user");
        assertThat(result.path("type").asText()).isEqualTo("tool_result");
        assertThat(result.path("tool_use_id").asText()).isEqualTo("toolu_1");
        assertThat(result.path("is_error").asBoolean()).isTrue();
    }

    @Test
    void responseTextAndToolCallsAreParsed() throws IOException {
        ModelReply reply = model("key", "http://localhost").parse(mapper.readTree("""
                {"content":[
                  {"type":"text","text":"Посмотрю свободное время."},
                  {"type":"tool_use","id":"toolu_2","name":"find_free_slots","input":{"service_id":3}},
                  {"type":"text","text":"Минутку."}
                ],"stop_reason":"tool_use","usage":{"input_tokens":10,"output_tokens":5}}
                """));

        assertThat(reply.text()).isEqualTo("Посмотрю свободное время.\n\nМинутку.");
        assertThat(reply.wantsTools()).isTrue();
        assertThat(reply.toolCalls().get(0).name()).isEqualTo("find_free_slots");
        assertThat(reply.toolCalls().get(0).input().path("service_id").asInt()).isEqualTo(3);
    }

    @Test
    void overloadedApiIsRetriedOnceWithKeyAndVersionHeaders() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        List<String> keys = new CopyOnWriteArrayList<>();
        List<String> versions = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("x-api-key"));
            versions.add(exchange.getRequestHeaders().getFirst("anthropic-version"));
            exchange.getRequestBody().readAllBytes();
            boolean first = calls.incrementAndGet() == 1;
            respond(exchange, first ? 529 : 200, first
                    ? "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}"
                    : "{\"content\":[{\"type\":\"text\",\"text\":\"Здравствуйте!\"}],\"stop_reason\":\"end_turn\"}");
        });
        server.start();

        ModelReply reply = model("secret-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .reply("s", List.of(new UserText("Привет")), TOOLS);

        assertThat(reply.text()).isEqualTo("Здравствуйте!");
        assertThat(reply.wantsTools()).isFalse();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(keys).containsOnly("secret-key");
        assertThat(versions).containsOnly(AnthropicChatModel.API_VERSION);
    }

    @Test
    void apiErrorMessageIsReported() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
                    + "\"message\":\"invalid x-api-key\"}}");
        });
        server.start();

        assertThatThrownBy(() -> model("wrong", "http://127.0.0.1:" + server.getAddress().getPort())
                .reply("s", List.of(new UserText("Привет")), TOOLS))
                .isInstanceOf(ChatModelException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("invalid x-api-key");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
