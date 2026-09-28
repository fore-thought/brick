package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class OutputNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new OutputNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(EdgeKeys.MESSAGES, List.of(
                new Message.UserMessage("hi"),
                new Message.AssistantMessage("answer", List.of())));
    }

    @Test
    void extractsLastAssistantContent() {
        var result = subject().execute(sampleInput(), context());
        assertEquals("answer", result.get(AgentKeys.OUTPUT));
    }

    @Test
    void noAssistantMessageFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(
                        Map.of(EdgeKeys.MESSAGES, List.of(new Message.UserMessage("hi"))),
                        context()));
        assertEquals("output node: conversation contains no assistant message", e.getMessage());
    }
}
