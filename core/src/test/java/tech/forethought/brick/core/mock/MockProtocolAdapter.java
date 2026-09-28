package tech.forethought.brick.core.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.model.ToolCall;
import tech.forethought.brick.core.model.ToolDefinition;
import tech.forethought.brick.core.spi.ProtocolAdapter;

/**
 * Deterministic in-memory adapter for tests. Behavior: if the conversation
 * contains no tool result, the response requests the {@code echo} tool;
 * otherwise it answers {@code "done"}. Thread-safe (stateless).
 */
public final class MockProtocolAdapter implements ProtocolAdapter {

    public static final String NAME = "mock";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProtocolRequest convertRequest(List<Message> messages, List<ToolDefinition> tools,
                                          ModelConfig config) {
        var hasToolResult = messages.stream()
                .anyMatch(Message.ToolResultMessage.class::isInstance);
        return new ProtocolRequest(NAME,
                Map.of("messageCount", messages.size(), "hasToolResult", hasToolResult));
    }

    @Override
    public Stream<ProtocolResponse> call(ProtocolRequest request) {
        return Stream.of(new ProtocolResponse(NAME, request.payload()));
    }

    @Override
    public Message.AssistantMessage convertResponse(Stream<ProtocolResponse> responses) {
        var hasToolResult = responses
                .map(ProtocolResponse::payload)
                .anyMatch(p -> Boolean.TRUE.equals(p.get("hasToolResult")));
        if (hasToolResult) {
            return new Message.AssistantMessage("done", List.of());
        }
        return new Message.AssistantMessage("",
                List.of(new ToolCall("call-1", "echo", Map.of("text", "hello"))));
    }
}
