package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;

/**
 * Reusable contract suite for {@link Node}. Every implementation, including
 * third-party ones, runs this same suite to prove replaceability: subclass
 * it and provide the node under test plus suitable sample data.
 */
public abstract class NodeContractTest {

    protected abstract Node subject();

    /** Sample edge data suited to the node under test. */
    protected Map<String, Object> sampleInput() {
        return Map.of();
    }

    /** Sample node configuration suited to the node under test. */
    protected Map<String, Object> sampleConfig() {
        return Map.of();
    }

    /** Hook to register the services the node under test needs. */
    protected void configureServices(ManualServices services) {
    }

    protected final NodeContext context() {
        var services = new ManualServices();
        configureServices(services);
        return new NodeContext("test-run", "test-node", sampleConfig(), services);
    }

    @Test
    void typeIsNotBlank() {
        assertFalse(subject().type().isBlank());
    }

    @Test
    void executeReturnsNonNull() {
        assertNotNull(subject().execute(sampleInput(), context()));
    }

    @Test
    void doesNotMutateInput() {
        var input = new LinkedHashMap<>(sampleInput());
        var before = Map.copyOf(input);
        subject().execute(input, context());
        assertEquals(before, input);
    }
}
