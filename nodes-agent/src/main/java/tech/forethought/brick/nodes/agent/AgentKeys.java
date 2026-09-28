package tech.forethought.brick.nodes.agent;

/**
 * Edge-data keys of the default agent chain. Key names are protocol:
 * renaming is a breaking change.
 */
public final class AgentKeys {

    private AgentKeys() {
    }

    /** The raw run input (e.g. user text): {@code String}. */
    public static final String INPUT = "input";

    /** Tool definitions assembled for the model: {@code List<ToolDefinition>}. */
    public static final String TOOL_DEFINITIONS = "toolDefinitions";

    /** The protocol-shaped request: {@code ProtocolRequest}. */
    public static final String PROTOCOL_REQUEST = "protocolRequest";

    /** The protocol-shaped response chunks: {@code List<ProtocolResponse>}. */
    public static final String PROTOCOL_RESPONSES = "protocolResponses";

    /** Whether the last assistant message carries tool calls: {@code Boolean}. */
    public static final String HAS_TOOL_CALLS = "hasToolCalls";

    /** The final answer text: {@code String}. */
    public static final String OUTPUT = "output";
}
