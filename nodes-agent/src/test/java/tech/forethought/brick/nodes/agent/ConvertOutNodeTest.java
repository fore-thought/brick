package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.mock.MockProtocolAdapter;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class ConvertOutNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new ConvertOutNode();
    }

    @Override
    protected void configureServices(ManualServices services) {
        services.with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter());
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(
                EdgeKeys.MESSAGES, List.of(new Message.UserMessage("hi")),
                "llm.protocol", "mock",
                "llm.base-url", "http://localhost",
                "llm.api-key", "none",
                "llm.model", "mock-model");
    }

    @Test
    void producesProtocolRequest() {
        var result = subject().execute(sampleInput(), context());
        var request = (ProtocolRequest) result.get(AgentKeys.PROTOCOL_REQUEST);
        assertEquals("mock", request.protocol());
    }

    @Test
    void missingModelConfigFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(EdgeKeys.MESSAGES, List.of()), context()));
        assertTrue(e.getMessage().contains("llm.protocol"));
    }
}
