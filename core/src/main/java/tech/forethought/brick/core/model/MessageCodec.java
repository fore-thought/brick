package tech.forethought.brick.core.model;

import java.util.List;
import java.util.Map;

/**
 * Maps between {@link Message} records and their portable map form (the data
 * protocol format used in event payloads and persistence). Static and
 * thread-safe.
 */
public final class MessageCodec {

    private MessageCodec() {
    }

    /** Converts a message to its map form. */
    public static Map<String, Object> toMap(Message message) {
        return switch (message) {
            case Message.UserMessage m -> Map.of("role", "user", "content", m.content());
            case Message.SystemMessage m -> Map.of("role", "system", "content", m.content());
            case Message.ToolResultMessage m -> Map.of("role", "tool",
                    "toolCallId", m.toolCallId(), "content", m.content());
            case Message.AssistantMessage m -> Map.of("role", "assistant", "content",
                    m.content(), "toolCalls", m.toolCalls().stream().map(MessageCodec::toMap).toList());
            case null, default -> throw new IllegalArgumentException("unsupported message: " + message);
        };
    }

    /** Parses the map form back into a message. */
    @SuppressWarnings("unchecked")
    public static Message fromMap(Map<String, Object> map) {
        var role = String.valueOf(map.get("role"));
        var content = String.valueOf(map.get("content"));
        return switch (role) {
            case "user" -> new Message.UserMessage(content);
            case "system" -> new Message.SystemMessage(content);
            case "tool" -> new Message.ToolResultMessage(String.valueOf(map.get("toolCallId")),
                    content);
            case "assistant" -> new Message.AssistantMessage(content,
                    ((List<Map<String, Object>>) map.getOrDefault("toolCalls", List.of())).stream()
                            .map(MessageCodec::toToolCall)
                            .toList());
            case null, default -> throw new IllegalArgumentException("unsupported role: " + role);
        };
    }

    private static Map<String, Object> toMap(ToolCall call) {
        return Map.of("id", call.id(), "toolName", call.toolName(), "arguments",
                call.arguments());
    }

    @SuppressWarnings("unchecked")
    private static ToolCall toToolCall(Map<String, Object> map) {
        return new ToolCall(String.valueOf(map.get("id")), String.valueOf(map.get("toolName")),
                (Map<String, Object>) map.getOrDefault("arguments", Map.of()));
    }
}
