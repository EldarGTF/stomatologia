package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stomatologia.backend.assistant.ChatModel.AssistantTurn;
import com.stomatologia.backend.assistant.ChatModel.ModelMessage;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolResults;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ход модели с инструментами в виде служебного сообщения TOOL: вызовы и их результаты.
 * Из него история для модели восстанавливается так, как модель её видела.
 */
final class ToolTurns {

    /** Результаты списков бывают длинными; для памяти модели хватает начала. */
    static final int MAX_RESULT = 4000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolTurns() {
    }

    static String write(AssistantTurn turn, List<ToolResult> results) {
        ObjectNode root = JSON.createObjectNode();
        if (turn.text() != null && !turn.text().isBlank()) {
            root.put("text", turn.text());
        }
        ArrayNode calls = root.putArray("calls");
        for (ToolCall c : turn.toolCalls()) {
            ObjectNode node = calls.addObject();
            node.put("id", c.id());
            node.put("name", c.name());
            node.set("input", c.input() == null ? JSON.createObjectNode() : c.input());
        }
        ArrayNode out = root.putArray("results");
        for (ToolResult r : results) {
            String content = r.content() == null ? "" : r.content();
            ObjectNode node = out.addObject();
            node.put("id", r.toolCallId());
            node.put("content", content.length() <= MAX_RESULT ? content : content.substring(0, MAX_RESULT) + "…");
            if (r.error()) {
                node.put("error", true);
            }
        }
        return root.toString();
    }

    /** Ход и его результаты; пусто, если запись повреждена. */
    static List<ModelMessage> read(String stored) {
        JsonNode root;
        try {
            root = JSON.readTree(stored);
        } catch (IOException e) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode c : root.path("calls")) {
            calls.add(new ToolCall(c.path("id").asText(), c.path("name").asText(), c.path("input")));
        }
        List<ToolResult> results = new ArrayList<>();
        for (JsonNode r : root.path("results")) {
            results.add(new ToolResult(r.path("id").asText(), r.path("content").asText(), r.path("error").asBoolean()));
        }
        if (calls.isEmpty() || calls.size() != results.size()) {
            return List.of();
        }
        String text = root.path("text").isTextual() ? root.path("text").asText() : null;
        return List.of(new AssistantTurn(text, calls), new ToolResults(results));
    }
}
