package com.stomatologia.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stomatologia.backend.assistant.ChatModel.AssistantTurn;
import com.stomatologia.backend.assistant.ChatModel.ModelMessage;
import com.stomatologia.backend.assistant.ChatModel.ToolCall;
import com.stomatologia.backend.assistant.ChatModel.ToolResult;
import com.stomatologia.backend.assistant.ChatModel.ToolResults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolTurnsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void turnIsRestoredAsModelSawIt() throws Exception {
        ToolCall call = new ToolCall("toolu_1", "find_free_slots",
                mapper.readTree("{\"service\":\"Лечение кариеса\",\"date\":\"2026-10-09\"}"));
        AssistantTurn turn = new AssistantTurn("Смотрю расписание", List.of(call));
        List<ToolResult> results = List.of(new ToolResult("toolu_1", "{\"slots\":[\"15:00\"]}", false));

        List<ModelMessage> restored = ToolTurns.read(ToolTurns.write(turn, results));

        assertThat(restored).containsExactly(turn, new ToolResults(results));
    }

    @Test
    void errorFlagAndLongResultsAreKeptCompact() throws Exception {
        ToolCall call = new ToolCall("toolu_2", "list_services", mapper.readTree("{}"));
        String longResult = "x".repeat(ToolTurns.MAX_RESULT + 500);

        List<ModelMessage> restored = ToolTurns.read(ToolTurns.write(new AssistantTurn(null, List.of(call)),
                List.of(new ToolResult("toolu_2", longResult, true))));

        ToolResult result = ((ToolResults) restored.get(1)).results().get(0);
        assertThat(result.error()).isTrue();
        assertThat(result.content()).hasSize(ToolTurns.MAX_RESULT + 1).endsWith("…");
        assertThat(((AssistantTurn) restored.get(0)).text()).isNull();
    }

    @Test
    void damagedRecordIsSkipped() {
        assertThat(ToolTurns.read("не json")).isEmpty();
        assertThat(ToolTurns.read("{\"calls\":[{\"id\":\"a\",\"name\":\"x\"}],\"results\":[]}")).isEmpty();
    }
}
