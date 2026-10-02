package tech.forethought.brick.nodes.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.MessageCodec;
import tech.forethought.brick.core.model.ProtocolResponse;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.spi.ProtocolAdapter;

/**
 * Inbound conversion node: folds response chunks into an assistant message,
 * appends it to the conversation (rewriting the {@code "messages"} pin), and
 * flags tool-call presence on {@code "hasTools"}. Thread-safe (stateless).
 */
public final class ConvertInNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "convert-in";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract(Map<String, Object> config) {
        return new NodeContract(
                List.of(new Key(AgentKeys.PROTOCOL_RESPONSES, ValueType.LIST),
                        new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST),
                        new Key(AgentKeys.HAS_TOOL_CALLS, ValueType.BOOLEAN)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var responses = (List<ProtocolResponse>) input.get(AgentKeys.PROTOCOL_RESPONSES);
        if (responses == null || responses.isEmpty()) {
            throw new IllegalArgumentException(
                    "convert-in node: input is missing key '" + AgentKeys.PROTOCOL_RESPONSES + "'");
        }
        var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
        if (messages == null) {
            throw new IllegalArgumentException(
                    "convert-in node: input is missing key '" + EdgeKeys.MESSAGES + "'");
        }
        var adapter = context.services().require(ProtocolAdapter.class,
                responses.getFirst().protocol());
        var assistantMessage = adapter.convertResponse(responses.stream());
        context.events().emit(EventKinds.MESSAGE_APPENDED,
                Map.of("message", MessageCodec.toMap(assistantMessage)));
        var history = new ArrayList<>(messages);
        history.add(assistantMessage);
        var out = new LinkedHashMap<String, Object>();
        out.put(EdgeKeys.MESSAGES, List.copyOf(history));
        out.put(AgentKeys.HAS_TOOL_CALLS, !assistantMessage.toolCalls().isEmpty());
        return Map.copyOf(out);
    }
}
