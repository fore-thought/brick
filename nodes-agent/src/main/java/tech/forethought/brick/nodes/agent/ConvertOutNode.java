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
 * Outbound conversion node: conversation + tool definitions + the model
 * configuration (the {@code "llm"} MAP pin, carrying {@code "llm.protocol"}
 * / {@code "llm.base-url"} / {@code "llm.api-key"} / {@code "llm.model"})
 * become a protocol request via the configured adapter. Thread-safe
 * (stateless).
 */
public final class ConvertOutNode implements Node {

    /** The spec type name of this node. */
    public static final String TYPE = "convert-out";

    /** The read pin carrying the model configuration as a MAP. */
    public static final String LLM_PIN = "llm";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public NodeContract contract() {
        // tools and llm are bound once and stay sticky across loop refires
        return new NodeContract(
                List.of(new Key(EdgeKeys.MESSAGES, ValueType.LIST)),
                List.of(new Key(AgentKeys.TOOL_DEFINITIONS, ValueType.LIST),
                        new Key(LLM_PIN, ValueType.MAP)),
                List.of(new Key(AgentKeys.PROTOCOL_REQUEST, ValueType.ANY)), false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, NodeContext context) {
        var llm = (Map<String, Object>) input.get(LLM_PIN);
        if (llm == null) {
            throw new IllegalArgumentException(
                    "convert-out node: input is missing key '" + LLM_PIN + "'");
        }
        var protocol = required(llm, "llm.protocol");
        var config = new ModelConfig(protocol,
                required(llm, "llm.base-url"),
                required(llm, "llm.api-key"),
                required(llm, "llm.model"),
                Map.of());
        var messages = (List<Message>) input.get(EdgeKeys.MESSAGES);
        if (messages == null) {
            throw new IllegalArgumentException(
                    "convert-out node: input is missing key '" + EdgeKeys.MESSAGES + "'");
        }
        var tools = (List<ToolDefinition>) input.getOrDefault(AgentKeys.TOOL_DEFINITIONS, List.of());
        var adapter = context.services().require(ProtocolAdapter.class, protocol);
        var out = new LinkedHashMap<String, Object>();
        out.put(AgentKeys.PROTOCOL_REQUEST, adapter.convertRequest(messages, tools, config));
        return Map.copyOf(out);
    }

    private static String required(Map<String, Object> llm, String key) {
        var value = llm.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "convert-out node: llm config is missing key '" + key + "'");
        }
        return String.valueOf(value);
    }
}
