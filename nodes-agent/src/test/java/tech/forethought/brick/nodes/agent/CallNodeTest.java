package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.mock.MockProtocolAdapter;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.testkit.RecordingEmitter;
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

    @Test
    void emitsTokenDeltasFromStreamingAdapters() {
        var streaming = new ProtocolAdapter() {
            @Override
            public String name() {
                return "streaming-mock";
            }

            @Override
            public tech.forethought.brick.core.model.ProtocolRequest convertRequest(
                    List<Message> messages,
                    List<tech.forethought.brick.core.model.ToolDefinition> tools,
                    ModelConfig config) {
                return new tech.forethought.brick.core.model.ProtocolRequest("streaming-mock",
                        Map.of());
            }

            @Override
            public Stream<ProtocolResponse> call(
                    tech.forethought.brick.core.model.ProtocolRequest request) {
                return Stream.of(new ProtocolResponse("streaming-mock", Map.of()));
            }

            @Override
            public Message.AssistantMessage convertResponse(Stream<ProtocolResponse> responses) {
                return new Message.AssistantMessage("ok", List.of());
            }

            @Override
            public String textDelta(ProtocolResponse chunk) {
                return "token";
            }
        };
        var emitter = new RecordingEmitter();
        var context = new NodeContext("run", "n", Map.of(),
                new ManualServices().with(ProtocolAdapter.class, "streaming-mock", streaming),
                emitter);
        var input = Map.<String, Object>of(AgentKeys.PROTOCOL_REQUEST,
                new tech.forethought.brick.core.model.ProtocolRequest("streaming-mock",
                        Map.of()));
        new CallNode().execute(input, context);
        assertEquals(1, emitter.events().size());
        assertEquals(EventKinds.TOKEN_DELTA, emitter.events().getFirst().kind());
        assertEquals("token", emitter.events().getFirst().payload().get("text"));
    }
}
