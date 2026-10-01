package tech.forethought.brick.core.spi;

/**
 * Pin-name conventions shared across first-party nodes. Pin names are
 * protocol: renaming is a breaking change. Custom nodes may add their own
 * names; a name-spaced prefix is recommended to avoid collisions.
 */
public final class EdgeKeys {

    private EdgeKeys() {
    }

    /** The conversation so far, as a node-local pin: {@code List<Message>}. */
    public static final String MESSAGES = "messages";
}
