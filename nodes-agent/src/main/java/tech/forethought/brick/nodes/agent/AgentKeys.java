package tech.forethought.brick.nodes.agent;

/**
 * Pin-name conventions of the first-party agent-chain nodes. The constants
 * are only a naming convention — the "global" color of edge-data keys is
 * gone; every pin is node-local and wiring translates between names. Pin
 * names are protocol: renaming is a breaking change.
 */
public final class AgentKeys {

    private AgentKeys() {
    }

    /** The run input text (input node's read pin): {@code String}. */
    public static final String INPUT = "text";

    /** The conversation so far (input node's context pin): {@code List<Message>}. */
    public static final String HISTORY = "history";

    /** Tool definitions assembled for the model: {@code List<ToolDefinition>}. */
    public static final String TOOL_DEFINITIONS = "tools";

    /** The protocol-shaped request: {@code ProtocolRequest}. */
    public static final String PROTOCOL_REQUEST = "request";

    /** The protocol-shaped response chunks: {@code List<ProtocolResponse>}. */
    public static final String PROTOCOL_RESPONSES = "responses";

    /** Whether the last assistant message carries tool calls: {@code Boolean}. */
    public static final String HAS_TOOL_CALLS = "hasTools";

    /** The final answer text: {@code String}. */
    public static final String OUTPUT = "output";
}
