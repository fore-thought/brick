package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.mock.MockProtocolAdapter;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class ConvertInNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new ConvertInNode();
    }

    @Override
    protected void configureServices(ManualServices services) {
        services.with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter());
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(
                EdgeKeys.MESSAGES, List.of(new Message.UserMessage("hi")),
                AgentKeys.PROTOCOL_RESPONSES,
                List.of(new ProtocolResponse("mock", Map.of("hasToolResult", true))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void foldsResponseAndAppendsToConversation() {
        var result = subject().execute(sampleInput(), context());
        var messages = (List<Message>) result.get(EdgeKeys.MESSAGES);
        assertEquals(2, messages.size());
        assertEquals(new Message.AssistantMessage("done", List.of()), messages.get(1));
        assertEquals(false, result.get(AgentKeys.HAS_TOOL_CALLS));
    }
}
