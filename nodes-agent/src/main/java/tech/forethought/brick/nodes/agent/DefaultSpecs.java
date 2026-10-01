package tech.forethought.brick.nodes.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spec.PipelineSpecCodec;

/**
 * Factory for the default graphs. Graphs are data: the default chat chain
 * itself ships as {@code chat.json} on the classpath, loaded through the
 * platform codec and freely replaceable by user-drawn specs.
 */
public final class DefaultSpecs {

    private DefaultSpecs() {
    }

    /** The default graph name for the chat chain. */
    public static final String CHAT = "chat";

    /** The default config file path used by {@link #chat()}. */
    public static final String DEFAULT_CONFIG_PATH = "config/llm.properties";

    /** The id of the config-loader node in {@code chat.json}. */
    private static final String CONFIG_NODE_ID = "config";

    /**
     * The default chat chain: config loading, input, context assembly,
     * outbound conversion, the model call, inbound conversion, the tool-call
     * loop (via a selective-delivery gateway), and the output terminal.
     */
    public static PipelineSpec chat() {
        return chat(DEFAULT_CONFIG_PATH);
    }

    /**
     * The default chat chain with a custom config file path (see
     * {@link #chat()}).
     */
    public static PipelineSpec chat(String configPath) {
        var spec = readChatJson();
        if (DEFAULT_CONFIG_PATH.equals(configPath)) {
            return spec;
        }
        var nodes = spec.nodes().stream()
                .map(node -> CONFIG_NODE_ID.equals(node.id())
                        ? new NodeSpec(node.id(), node.type(), Map.of("path", configPath))
                        : node)
                .toList();
        return new PipelineSpec(spec.name(), nodes, spec.edges(), spec.inputs(), spec.outputs(),
                spec.maxFirings());
    }

    private static PipelineSpec readChatJson() {
        var resource = DefaultSpecs.class.getResource("chat.json");
        if (resource == null) {
            throw new IllegalStateException("bundled chat.json is missing from the classpath");
        }
        try (var in = resource.openStream()) {
            return PipelineSpecCodec.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read bundled chat.json", e);
        }
    }
}
