package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class PassNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new PassNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of("value", 42);
    }

    @Test
    void forwardsValueUnchanged() {
        var result = subject().execute(sampleInput(), context());
        assertEquals(Map.of("value", 42), result);
    }

    @Test
    void missingValueFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context()));
        assertEquals("pass node: input is missing key 'value'", e.getMessage());
    }
}
