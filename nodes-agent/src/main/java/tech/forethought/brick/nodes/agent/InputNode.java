package tech.forethought.brick.nodes.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;

/**
 * Entry node of the default chain: wraps the run input (key
 * {@code "input"}) into a user message appended to the conversation.
 * Thread-safe (stateless).
 */
public final class InputNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "input";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var text = input.get(AgentKeys.INPUT);
        if (text == null) {
            throw new IllegalArgumentException("input node: edge data is missing key 'input'");
        }
        var messages = new ArrayList<>(
                (List<Message>) input.getOrDefault(EdgeKeys.MESSAGES, List.of()));
        messages.add(new Message.UserMessage(String.valueOf(text)));
        var out = new LinkedHashMap<>(input);
        out.put(EdgeKeys.MESSAGES, List.copyOf(messages));
        return out;
    }
}
