package tech.forethought.brick.nodes.agent;

import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.nodes.basic.ConfigLoaderNode;
import tech.forethought.brick.nodes.basic.IfNode;

/**
 * Factory for the default graphs. Graphs are data; these are convenience
 * assemblies, freely replaceable by user-drawn specs.
 */
public final class DefaultSpecs {

    private DefaultSpecs() {
    }

    /** The default graph name for the chat chain. */
    public static final String CHAT = "chat";

    /** The default config file path used by {@link #chat()}. */
    public static final String DEFAULT_CONFIG_PATH = "config/llm.properties";

    /**
     * The default chat chain: config loading, input, context assembly,
     * outbound conversion, the model call, inbound conversion, the tool-call
     * loop (via an if gateway), and the output terminal.
     */
    public static PipelineSpec chat() {
        return chat(DEFAULT_CONFIG_PATH);
    }

    /**
     * The default chat chain with a custom config file path (see
     * {@link #chat()}).
     */
    public static PipelineSpec chat(String configPath) {
        return new PipelineSpec(CHAT,
                List.of(
                        new NodeSpec("config", ConfigLoaderNode.TYPE,
                                Map.of("path", configPath)),
                        new NodeSpec("input", InputNode.TYPE, Map.of()),
                        new NodeSpec("preprocess", ContextPreprocessNode.TYPE, Map.of()),
                        new NodeSpec("convert", ConvertOutNode.TYPE, Map.of()),
                        new NodeSpec("call", CallNode.TYPE, Map.of()),
                        new NodeSpec("convert-back", ConvertInNode.TYPE, Map.of()),
                        new NodeSpec("has-tools", IfNode.TYPE,
                                Map.of("key", AgentKeys.HAS_TOOL_CALLS, "equals", true)),
                        new NodeSpec("exec", ToolExecNode.TYPE, Map.of()),
                        new NodeSpec("out", OutputNode.TYPE, Map.of())),
                List.of(
                        new EdgeSpec("config", "input", null),
                        new EdgeSpec("input", "preprocess", null),
                        new EdgeSpec("preprocess", "convert", null),
                        new EdgeSpec("convert", "call", null),
                        new EdgeSpec("call", "convert-back", null),
                        new EdgeSpec("convert-back", "has-tools", null),
                        new EdgeSpec("has-tools", "exec", "true"),
                        new EdgeSpec("has-tools", "out", "false"),
                        new EdgeSpec("exec", "convert", null)),
                "config");
    }
}
