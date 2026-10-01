package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventEmitter;
import tech.forethought.brick.core.mock.EchoTool;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.Tool;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class ContextPreprocessNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new ContextPreprocessNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(EdgeKeys.MESSAGES, List.of(new Message.UserMessage("hi")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void prependsSystemPromptOnce() {
        var node = new ContextPreprocessNode();
        var context = new NodeContext("run", "n", Map.of("systemPrompt", "be brief"),
                new ManualServices(), EventEmitter.noop());
        var first = node.execute(sampleInput(), context);
        var messages = (List<Message>) first.get(EdgeKeys.MESSAGES);
        assertEquals(2, messages.size());
        assertEquals(new Message.SystemMessage("be brief"), messages.getFirst());
        // idempotent when a system message already leads
        var second = node.execute(Map.of(EdgeKeys.MESSAGES, messages), context);
        assertEquals(2, ((List<?>) second.get(EdgeKeys.MESSAGES)).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesToolDefinitionsByName() {
        var node = new ContextPreprocessNode();
        var services = new ManualServices().with(Tool.class, "echo", new EchoTool());
        var context = new NodeContext("run", "n", Map.of("tools", List.of("echo")), services,
                EventEmitter.noop());
        var result = node.execute(Map.of(), context);
        var definitions = (List<?>) result.get(AgentKeys.TOOL_DEFINITIONS);
        assertEquals(1, definitions.size());
    }
}
