package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventEmitter;
import tech.forethought.brick.core.spi.EdgeKeys;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.NodeContext;
import tech.forethought.brick.core.testkit.ManualServices;
import tech.forethought.brick.core.testkit.NodeContractTest;

public final class SwitchNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new SwitchNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of("kind", "b");
    }

    @Override
    protected Map<String, Object> sampleConfig() {
        return Map.of("key", "kind");
    }

    @Test
    void routesByStringFormOfValue() {
        var context = new NodeContext("run", "n", sampleConfig(), new ManualServices(), EventEmitter.noop());
        assertEquals("b", subject().execute(sampleInput(), context).get(EdgeKeys.ROUTE));
    }

    @Test
    void missingInputKeyFailsClearly() {
        var context = new NodeContext("run", "n", sampleConfig(), new ManualServices(), EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context));
        assertEquals("switch node: input is missing key 'kind'", e.getMessage());
    }
}
