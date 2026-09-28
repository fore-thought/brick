package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spi.StateStore;

/**
 * Reusable contract suite for {@link StateStore}. Subclass and provide the
 * store under test.
 */
public abstract class StateStoreContractTest {

    protected abstract StateStore subject();

    @Test
    void unknownSessionLoadsEmpty() {
        var messages = subject().loadSession("no-such-session");
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }
}
