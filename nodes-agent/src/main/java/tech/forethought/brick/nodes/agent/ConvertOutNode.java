package tech.forethought.brick.nodes.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.model.ToolDefinition;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;
import tech.forethought.brick.core.spi.ProtocolAdapter;

/**
 * Outbound conversion node: conversation + tool definitions + model config
 * (edge keys {@code "llm.protocol"} / {@code "llm.base-url"} /
 * {@code "llm.api-key"} / {@code "llm.model"}) become a protocol request via
 * the configured adapter. Thread-safe (stateless).
 */
public final class ConvertOutNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "convert-out";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        return new NodeContract(
                List.of(new Key("llm.protocol", ValueType.STRING),
                        new Key("llm.base-url", ValueType.STRING),
                        new Key("llm.api-key", ValueType.STRING),
                        new Key("llm.model", ValueType.STRING),
                        new Key(EdgeKeys.MESSAGES, ValueType.LIST),
                        new Key(AgentKeys.TOOL_DEFINITIONS, ValueType.LIST)),
                List.of(new Key(AgentKeys.PROTOCOL_REQUEST, ValueType.ANY)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var protocol = required(input, "llm.protocol");
        var config = new ModelConfig(protocol,
                required(input, "llm.base-url"),
                required(input, "llm.api-key"),
                required(input, "llm.model"),
                Map.of());
        var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
        if (messages == null) {
            throw new IllegalArgumentException(
                    "convert-out node: edge data is missing key 'messages'");
        }
        var tools = (List<ToolDefinition>) input.getOrDefault(AgentKeys.TOOL_DEFINITIONS, List.of());
        var adapter = context.services().require(ProtocolAdapter.class, protocol);
        var out = new LinkedHashMap<>(input);
        out.put(AgentKeys.PROTOCOL_REQUEST, adapter.convertRequest(messages, tools, config));
        return out;
    }

    private static String required(Map<String, Object> input, String key) {
        var value = input.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "convert-out node: edge data is missing key '" + key + "'");
        }
        return String.valueOf(value);
    }
}
