package tech.forethought.brick.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PipelineSpec;

class SpecValidatorTest {

    private static NodeSpec node(String id) {
        return new NodeSpec(id, "any", Map.of());
    }

    @Test
    void validSpecProducesNoDiagnostics() {
        var spec = new PipelineSpec("ok",
                List.of(node("a"), node("b")),
                List.of(new EdgeSpec("a", "b", null)),
                "a");
        assertTrue(SpecValidator.validate(spec).isEmpty());
    }

    @Test
    void duplicateNodeIdsAreErrors() {
        var spec = new PipelineSpec("dup",
                List.of(node("a"), node("a")),
                List.of(),
                "a");
        var diagnostics = SpecValidator.validate(spec);
        assertTrue(diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR
                && d.message().contains("duplicate")));
    }

    @Test
    void missingEntryIsAnError() {
        var spec = new PipelineSpec("no-entry",
                List.of(node("a")),
                List.of(),
                "nowhere");
        var diagnostics = SpecValidator.validate(spec);
        assertTrue(diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR
                && d.message().contains("entry")));
    }

    @Test
    void danglingEdgeIsAnError() {
        var spec = new PipelineSpec("dangling",
                List.of(node("a")),
                List.of(new EdgeSpec("a", "ghost", null)),
                "a");
        var diagnostics = SpecValidator.validate(spec);
        assertTrue(diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR
                && d.message().contains("target")));
    }

    @Test
    void unreachableNodeIsAWarning() {
        var spec = new PipelineSpec("unreachable",
                List.of(node("a"), node("b"), node("orphan")),
                List.of(new EdgeSpec("a", "b", null)),
                "a");
        var diagnostics = SpecValidator.validate(spec);
        assertEquals(1, diagnostics.size());
        assertEquals(Diagnostic.Severity.WARNING, diagnostics.getFirst().severity());
        assertTrue(diagnostics.getFirst().location().contains("orphan"));
    }
}
