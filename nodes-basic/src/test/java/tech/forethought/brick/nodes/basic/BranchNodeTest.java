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

public final class BranchNodeTest extends NodeContractTest {

    @Override
    protected Node subject() {
        return new BranchNode();
    }

    @Override
    protected Map<String, Object> sampleInput() {
        return Map.of("control", true, "value", "payload");
    }

    private Map<String, Object> run(Map<String, Object> input, Map<String, Object> config) {
        var context = new NodeContext("run", "n", config, new ManualServices(),
                EventEmitter.noop());
        return subject().execute(input, context);
    }

    @Test
    void booleanControlDegradesToTrueFalsePins() {
        assertEquals(Map.of("true", "payload"), run(sampleInput(), Map.of()));
        assertFalse(run(sampleInput(), Map.of()).containsKey("false"));
        assertEquals(Map.of("false", "payload"),
                run(Map.of("control", false, "value", "payload"), Map.of()));
    }

    @Test
    void deliversUnderTheMatchingCase() {
        var config = Map.<String, Object>of("cases", List.of("a", "b"));
        assertEquals(Map.of("b", "payload"),
                run(Map.of("control", "b", "value", "payload"), config));
    }

    @Test
    void fallsBackToDefaultWhenNothingMatches() {
        var config = Map.<String, Object>of("cases", List.of("a", "b"));
        assertEquals(Map.of("default", "payload"),
                run(Map.of("control", "zzz", "value", "payload"), config));
    }

    @Test
    void matchesByStringForm() {
        var config = Map.<String, Object>of("cases", List.of("42"));
        assertEquals(Map.of("42", 42), run(Map.of("control", 42, "value", 42), config));
    }

    @Test
    void missingControlFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(Map.of("value", "payload"), Map.of()));
        assertEquals("branch node: input is missing key 'control'", e.getMessage());
    }

    @Test
    void missingValueFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(Map.of("control", true), Map.of()));
        assertEquals("branch node: input is missing key 'value'", e.getMessage());
    }

    @Test
    void nonBooleanControlWithoutCasesFailsClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(Map.of("control", "a", "value", 1), Map.of()));
        assertEquals("branch node requires config 'cases' unless control is a boolean",
                e.getMessage());
    }

    @Test
    void mistypedCasesFailClearly() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> run(sampleInput(), Map.of("cases", "a")));
        assertEquals("branch node requires config 'cases' as a list", e.getMessage());
    }
}
