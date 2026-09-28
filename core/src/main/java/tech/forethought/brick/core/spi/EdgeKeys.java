package tech.forethought.brick.core.spi;

/**
 * Protocol keys on edge data. Key names are protocol: renaming is a breaking
 * change. Custom nodes may add their own keys; a name-spaced prefix is
 * recommended to avoid collisions.
 */
public final class EdgeKeys {

    private EdgeKeys() {
    }

    /** The conversation so far: {@code List<Message>}. */
    public static final String MESSAGES = "messages";

    /**
     * Route value set by gateway nodes; consumed (and removed) by the engine
     * at the transition.
     */
    public static final String ROUTE = "route";
}
