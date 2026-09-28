package tech.forethought.brick.core.spi;

import java.util.List;
import tech.forethought.brick.core.model.Message;

/**
 * Read side of persistence: reconstructs a session's conversation.
 * Implementations are options; a store may also implement writing without
 * core assuming the two are the same thing.
 */
public interface StateStore {

    /** Returns the session's conversation in order; empty when unknown. */
    List<Message> loadSession(String sessionId);
}
