package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.mock.MockProtocolAdapter;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class CallNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new CallNode();
    }

    @Override
    protected void configureServices(ManualServices services) {
        services.with(ProtocolAdapter.class, MockProtocolAdapter.NAME, new MockProtocolAdapter());
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of(AgentKeys.PROTOCOL_REQUEST,
                new ProtocolRequest("mock", Map.of("hasToolResult", false)));
    }

    @Test
    void putsResponseChunksOnTheEdge() {
        var result = subject().execute(sampleInput(), context());
        var responses = (List<?>) result.get(AgentKeys.PROTOCOL_RESPONSES);
        assertEquals(1, responses.size());
    }

    @Test
    void missingRequestFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context()));
        assertTrue(e.getMessage().contains("protocolRequest"));
    }
}
