package tech.forethought.brick.nodes.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.event.EventEmitter;
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
        return Map.of("value", "b");
    }

    @Override
    protected Map<String, Object> sampleConfig() {
        return Map.of("cases", List.of("a", "b"));
    }

    private Map<String, Object> run(Map<String, Object> input, Map<String, Object> config) {
        var context = new NodeContext("run", "n", config, new ManualServices(),
                EventEmitter.noop());
        return subject().execute(input, context);
    }

    @Test
    void deliversUnderTheMatchingCase() {
        var result = run(Map.of("value", "b"), sampleConfig());
        assertEquals(Map.of("b", "b"), result);
        assertFalse(result.containsKey("default"));
    }

    @Test
    void deliversUnderDefaultWhenNoCaseMatches() {
        var result = run(Map.of("value", "zzz"), sampleConfig());
        assertEquals(Map.of("default", "zzz"), result);
    }

    @Test
    void matchesByStringForm() {
        var result = run(Map.of("value", 42), Map.of("cases", List.of("42")));
        assertEquals(Map.of("42", 42), result);
    }

    @Test
    void missingValueFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(Map.of(), sampleConfig()));
        assertEquals("switch node: input is missing key 'value'", e.getMessage());
    }

    @Test
    void missingCasesConfigFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(Map.of("value", "b"), Map.of()));
        assertEquals("switch node requires config 'cases'", e.getMessage());
    }
}
