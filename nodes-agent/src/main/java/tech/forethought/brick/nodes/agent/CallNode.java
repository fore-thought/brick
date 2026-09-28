package tech.forethought.brick.nodes.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.model.ProtocolRequest;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.ProtocolAdapter;

/**
 * The call node: performs the protocol call and puts the response chunks on
 * the edge. The adapter is selected by the request's protocol name.
 * Thread-safe (stateless).
 */
public final class CallNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "llm-call";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        if (!(input.get(AgentKeys.PROTOCOL_REQUEST) instanceof ProtocolRequest request)) {
            throw new IllegalArgumentException(
                    "llm-call node: edge data is missing key 'protocolRequest'");
        }
        var adapter = context.services().require(ProtocolAdapter.class, request.protocol());
        var events = context.events();
        var out = new LinkedHashMap<>(input);
        out.put(AgentKeys.PROTOCOL_RESPONSES, adapter.call(request)
                .peek(chunk -> {
                    var delta = adapter.textDelta(chunk);
                    if (!delta.isEmpty()) {
                        events.emit(EventKinds.TOKEN_DELTA, Map.of("text", delta));
                    }
                })
                .toList());
        return out;
    }
}
