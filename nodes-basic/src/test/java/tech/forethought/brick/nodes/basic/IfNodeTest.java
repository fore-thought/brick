package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventEmitter;
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
        return Map.of("control", true, "value", "payload");
    }

    private Map<String, Object> run(Map<String, Object> input) {
        var context = new NodeContext("run", "n", Map.of(), new ManualServices(),
                EventEmitter.noop());
        return subject().execute(input, context);
    }

    @Test
    void deliversUnderTrueOnlyWhenControlIsTrue() {
        var result = run(Map.of("control", true, "value", "payload"));
        assertEquals(Map.of("true", "payload"), result);
        assertFalse(result.containsKey("false"));
    }

    @Test
    void deliversUnderFalseOnlyWhenControlIsNotTrue() {
        var result = run(Map.of("control", false, "value", "payload"));
        assertEquals(Map.of("false", "payload"), result);
        assertFalse(result.containsKey("true"));
    }

    @Test
    void missingControlFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(Map.of("value", "payload")));
        assertEquals("if node: input is missing key 'control'", e.getMessage());
    }
}
