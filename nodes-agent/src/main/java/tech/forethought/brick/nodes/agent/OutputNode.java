package tech.forethought.brick.nodes.agent;

import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

/**
 * Terminal node of the default chain: extracts the last assistant message's
 * content into the {@code "output"} pin. Thread-safe (stateless).
 */
public final class OutputNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "output";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                List.of(new Key(AgentKeys.OUTPUT, ValueType.STRING)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
        if (messages == null) {
            throw new IllegalArgumentException(
                    "output node: input is missing key '" + EdgeKeys.MESSAGES + "'");
        }
        for (var i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof Message.AssistantMessage assistant) {
                return Map.of(AgentKeys.OUTPUT, assistant.content());
            }
        }
        throw new IllegalArgumentException(
                "output node: conversation contains no assistant message");
    }
}
