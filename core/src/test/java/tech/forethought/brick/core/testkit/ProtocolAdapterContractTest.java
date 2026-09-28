package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.model.Message;
import tech.forethought.brick.core.model.ModelConfig;
import tech.forethought.brick.core.spi.ProtocolAdapter;

/**
 * Reusable contract suite for {@link ProtocolAdapter}. Every implementation,
 * including third-party ones, runs this same suite to prove replaceability:
 * subclass it and provide the adapter under test plus a sample config.
 */
public abstract class ProtocolAdapterContractTest {

    protected abstract ProtocolAdapter subject();

    /** A minimal model config for the protocol under test. */
    protected abstract ModelConfig sampleConfig();

    @Test
    void nameIsNotBlank() {
        assertFalse(subject().name().isBlank());
    }

    @Test
    void roundTripThroughTheThreeStages() {
        var request = subject().convertRequest(
                List.of(new Message.UserMessage("hi")), List.of(), sampleConfig());
        assertNotNull(request);
        assertEquals(subject().name(), request.protocol());
        var responses = subject().call(request);
        assertNotNull(responses);
        assertNotNull(subject().convertResponse(responses));
    }
}
