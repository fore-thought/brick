package tech.forethought.brick.nodes.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.spi.Tool;

/**
 * Assembles conversation context: prepends the configured system prompt
 * (config {@code "systemPrompt"}, optional) and resolves tool definitions by
 * name (config {@code "tools"}: list of tool names, optional). Thread-safe
 * (stateless).
 */
public final class ContextPreprocessNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "context-preprocess";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        return new NodeContract(List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST),
                        new Key(AgentKeys.TOOL_DEFINITIONS, ValueType.LIST)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var out = new LinkedHashMap<>(input);
        var messages = new ArrayList<>(
                (List<Message>) input.getOrDefault(EdgeKeys.MESSAGES, List.of()));
        var systemPrompt = context.config().get("systemPrompt");
        if (systemPrompt != null
                && (messages.isEmpty() || !(messages.getFirst() instanceof Message.SystemMessage))) {
            messages.addFirst(new Message.SystemMessage(String.valueOf(systemPrompt)));
        }
        out.put(EdgeKeys.MESSAGES, List.copyOf(messages));

        var toolNames = (List<String>) context.config().getOrDefault("tools", List.of());
        var definitions = toolNames.stream()
                .map(name -> context.services().require(Tool.class, name).definition())
                .toList();
        out.put(AgentKeys.TOOL_DEFINITIONS, List.copyOf(definitions));
        return out;
    }
}
