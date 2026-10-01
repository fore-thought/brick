package tech.forethought.brick.nodes.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.event.EventKinds;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.MessageCodec;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Entry node of the default chain: appends the run input (read pin
 * {@code "text"}) to the injected conversation history (context pin
 * {@code "history"}, default empty) — a fresh conversation of
 * {@code history + [user message]} flows down the graph, so multi-turn
 * sessions stay visible to the model. Thread-safe (stateless).
 */
public final class InputNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "input";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        return new NodeContract(List.of(new Key(AgentKeys.INPUT, ValueType.STRING)),
                List.of(new Key(AgentKeys.HISTORY, ValueType.LIST)),
                List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        if (!input.containsKey(AgentKeys.INPUT)) {
            throw new IllegalArgumentException(
                    "input node: input is missing key '" + AgentKeys.INPUT + "'");
        }
        var message = new Message.UserMessage(String.valueOf(input.get(AgentKeys.INPUT)));
        context.events().emit(EventKinds.MESSAGE_APPENDED,
                Map.of("message", MessageCodec.toMap(message)));
        var messages = new ArrayList<>(
                (List<Message>) input.getOrDefault(AgentKeys.HISTORY, List.of()));
        messages.add(message);
        return Map.of(EdgeKeys.MESSAGES, List.copyOf(messages));
    }
}
