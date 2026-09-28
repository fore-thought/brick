package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.mock.EchoTool;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ToolCall;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.Tool;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class ToolExecNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new ToolExecNode();
    }

    @Override
    protected void configureServices(ManualServices services) {
        services.with(Tool.class, "echo", new EchoTool());
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(EdgeKeys.MESSAGES, List.of(
                new Message.UserMessage("hi"),
                new Message.AssistantMessage("",
                        List.of(new ToolCall("c1", "echo", Map.of("text", "hi"))))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendsToolResults() {
        var result = subject().execute(sampleInput(), context());
        var messages = (List<Message>) result.get(EdgeKeys.MESSAGES);
        assertEquals(3, messages.size());
        var toolResult = assertInstanceOf(Message.ToolResultMessage.class, messages.get(2));
        assertEquals("c1", toolResult.toolCallId());
    }
}
