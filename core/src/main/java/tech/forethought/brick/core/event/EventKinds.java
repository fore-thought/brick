package tech.forethought.brick.core.event;

/**
 * The core event kinds. Kind names are protocol: renaming is a breaking
 * change. Extensions may emit their own kinds.
 */
public final class EventKinds {

    private EventKinds() {
    }

    /** A run started. Durable. */
    public static final String RUN_START = "run-start";

    /**
     * A run ended. Payload carries a {@code status} of ok/error; ok adds
     * {@code firings} and the {@code neverFired} node ids. Durable.
     */
    public static final String RUN_END = "run-end";

    /** A node starts executing; payload summarizes or snapshots the input. Durable. */
    public static final String NODE_ENTER = "node-enter";

    /** A node finished; payload summarizes or snapshots the output. Durable. */
    public static final String NODE_EXIT = "node-exit";

    /** A message was appended to the conversation (payload carries it). Durable. */
    public static final String MESSAGE_APPENDED = "message-appended";

    /** Incremental response text for live display. Transient: never persisted. */
    public static final String TOKEN_DELTA = "token-delta";
}
