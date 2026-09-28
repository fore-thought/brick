package tech.forethought.brick.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageCodecTest {

    @Test
    void roundTripsUserMessage() {
        var message = new Message.UserMessage("hi");
        assertEquals(message, MessageCodec.fromMap(MessageCodec.toMap(message)));
    }

    @Test
    void roundTripsSystemMessage() {
        var message = new Message.SystemMessage("sys");
        assertEquals(message, MessageCodec.fromMap(MessageCodec.toMap(message)));
    }

    @Test
    void roundTripsAssistantMessageWithToolCalls() {
        var message = new Message.AssistantMessage("text",
                List.of(new ToolCall("c1", "echo", Map.of("text", "hi"))));
        assertEquals(message, MessageCodec.fromMap(MessageCodec.toMap(message)));
    }

    @Test
    void roundTripsToolResultMessage() {
        var message = new Message.ToolResultMessage("c1", "ok");
        assertEquals(message, MessageCodec.fromMap(MessageCodec.toMap(message)));
    }

    @Test
    void unknownRoleFails() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageCodec.fromMap(Map.of("role", "mystery", "content", "x")));
    }
}
