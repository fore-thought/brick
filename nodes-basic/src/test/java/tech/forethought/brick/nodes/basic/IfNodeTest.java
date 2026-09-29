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

public final class IfNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new IfNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of("flag", true);
    }

    @Override
    protected Map<String, Object> sampleConfig() {
        return Map.of("key", "flag", "equals", true);
    }

    private Map<String, Object> run(Map<String, Object> input) {
        var context = new NodeContext("run", "n", sampleConfig(), new ManualServices(), EventEmitter.noop());
        return subject().execute(input, context);
    }

    @Test
    void routesTrueWhenValueMatches() {
        assertEquals("true", run(Map.of("flag", true)).get(EdgeKeys.ROUTE));
    }

    @Test
    void routesFalseWhenValueDiffers() {
        assertEquals("false", run(Map.of("flag", false)).get(EdgeKeys.ROUTE));
    }

    @Test
    void missingKeyConfigFailsClearly() {
        var context = new NodeContext("run", "n", Map.of(), new ManualServices(), EventEmitter.noop());
        var e = assertThrows(IllegalArgumentException.class,
                () -> subject().execute(Map.of(), context));
        assertEquals("if node requires config 'key'", e.getMessage());
    }
}
