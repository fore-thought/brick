package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class InputNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new InputNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(AgentKeys.INPUT, "hi");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsRunInputIntoUserMessage() {
        var result = subject().execute(sampleInput(), context());
        var messages = (List<Message>) result.get(EdgeKeys.MESSAGES);
        assertEquals(List.of(new Message.UserMessage("hi")), messages);
    }

    @Test
    void missingInputKeyFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context()));
        assertTrue(e.getMessage().contains("'input'"));
    }

    @Test
    void emitsMessageAppended() {
        subject().execute(sampleInput(), context());
        var events = emittedEvents().events();
        assertEquals(1, events.size());
        assertEquals(EventKinds.MESSAGE_APPENDED, events.getFirst().kind());
        assertEquals(Map.of("role", "user", "content", "hi"),
                events.getFirst().payload().get("message"));
    }
}
