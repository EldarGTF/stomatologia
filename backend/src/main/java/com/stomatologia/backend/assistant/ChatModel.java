package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Языковая модель с вызовом инструментов. Разговор не зависит от поставщика: сменить Claude на другую модель —
 * значит написать ещё одну реализацию этого интерфейса.
 */
public interface ChatModel {

    /** false — ключ не задан: разговоры сразу передаются администратору. */
    boolean available();

    /**
     * Следующий ход модели: текст клиенту и (или) вызовы инструментов.
     *
     * @throws ChatModelException модель недоступна, не ответила вовремя или вернула ошибку
     */
    ModelReply reply(String system, List<ModelMessage> messages, List<ToolSpec> tools);

    /** Описание инструмента; inputSchema — JSON Schema параметров. */
    record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
    }

    record ToolCall(String id, String name, JsonNode input) {
    }

    record ToolResult(String toolCallId, String content, boolean error) {
    }

    record ModelReply(String text, List<ToolCall> toolCalls) {

        public boolean wantsTools() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    /** Сообщение истории в формате, общем для всех поставщиков. */
    sealed interface ModelMessage permits UserText, AssistantTurn, ToolResults {
    }

    record UserText(String text) implements ModelMessage {
    }

    /** Ответ модели: текст и вызовы инструментов, если были. */
    record AssistantTurn(String text, List<ToolCall> toolCalls) implements ModelMessage {

        public static AssistantTurn text(String text) {
            return new AssistantTurn(text, List.of());
        }
    }

    record ToolResults(List<ToolResult> results) implements ModelMessage {
    }
}
