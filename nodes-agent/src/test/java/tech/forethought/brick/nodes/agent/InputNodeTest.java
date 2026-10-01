package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
    @SuppressWarnings("unchecked")
    void splicesRunInputOntoInjectedHistory() {
        var input = Map.of(
                AgentKeys.INPUT, "hi",
                AgentKeys.HISTORY, List.of(
                        new Message.UserMessage("earlier question"),
                        new Message.AssistantMessage("earlier answer", List.of())));
        var messages = (List<Message>) subject().execute(input, context()).get(EdgeKeys.MESSAGES);
        assertEquals(List.of(
                new Message.UserMessage("earlier question"),
                new Message.AssistantMessage("earlier answer", List.of()),
                new Message.UserMessage("hi")), messages);
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotMutateInjectedHistory() {
        var history = new ArrayList<>(List.of(new Message.UserMessage("earlier")));
        var input = Map.<String, Object>of(AgentKeys.INPUT, "hi", AgentKeys.HISTORY, history);
        subject().execute(input, context());
        assertEquals(1, history.size());
    }

    @Test
    void missingTextPinFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context()));
        assertTrue(e.getMessage().contains("'text'"));
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
