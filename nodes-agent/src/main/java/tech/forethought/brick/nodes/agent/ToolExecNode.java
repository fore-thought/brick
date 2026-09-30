package tech.forethought.brick.nodes.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import tech.forethought.brick.core.spi.Tool;

/**
 * Executes the tool calls requested by the last assistant message and
 * appends their results to the conversation. Thread-safe (stateless).
 */
public final class ToolExecNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "tool-exec";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "tool-exec node: edge data is missing key 'messages'");
        }
        if (!(messages.getLast() instanceof Message.AssistantMessage last)) {
            throw new IllegalArgumentException(
                    "tool-exec node: last message is not an assistant message");
        }
        var history = new ArrayList<>(messages);
        for (var call : last.toolCalls()) {
            var tool = context.services().require(Tool.class, call.toolName());
            var result = new Message.ToolResultMessage(call.id(),
                    String.valueOf(tool.execute(call.arguments())));
            history.add(result);
            context.events().emit(EventKinds.MESSAGE_APPENDED,
                    Map.of("message", MessageCodec.toMap(result)));
        }
        var out = new LinkedHashMap<>(input);
        out.put(EdgeKeys.MESSAGES, List.copyOf(history));
        return out;
    }
}
