package tech.forethought.brick.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.NodeSpec;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.NodeContract;
import tech.forethought.brick.core.spi.NodeContract.Key;
import tech.forethought.brick.core.spi.NodeContract.ValueType;

class SpecValidatorTest {

    private static final NodeContract FORWARD = new NodeContract(
            List.of(new Key("in", ValueType.ANY)), List.of(new Key("out", ValueType.ANY)));

    private static NodeSpec node(String id) {
        return new NodeSpec(id, "any", Map.of());
    }

    private static EdgeSpec edge(String fromNode, String fromKey, String toNode, String toKey) {
        return new EdgeSpec(new PinRef(fromNode, fromKey), new PinRef(toNode, toKey));
    }

    private static boolean has(List<Diagnostic> diagnostics, Diagnostic.Severity severity,
                               String fragment) {
        return diagnostics.stream().anyMatch(d -> d.severity() == severity
                && d.message().contains(fragment));
    }

    @Test
    void validSpecProducesNoDiagnostics() {
        var spec = new PipelineSpec("ok",
                List.of(node("a"), node("b")),
                List.of(edge("a", "out", "b", "in")),
                List.of(new PinRef("a", "in")), List.of(new PinRef("b", "out")));
        assertTrue(SpecValidator.validate(spec).isEmpty());
    }

    @Test
    void duplicateNodeIdsAreErrors() {
        var spec = new PipelineSpec("dup",
                List.of(node("a"), node("a")),
                List.of(),
                List.of(), List.of());
        var diagnostics = SpecValidator.validate(spec);
        assertTrue(has(diagnostics, Diagnostic.Severity.ERROR, "duplicate"));
    }

    @Test
    void danglingEdgeEndsAreErrors() {
        var spec = new PipelineSpec("dangling",
                List.of(node("a")),
                List.of(edge("a", "out", "ghost", "in"), edge("ghost", "out", "a", "in")),
                List.of(), List.of());
        var diagnostics = SpecValidator.validate(spec);
        assertTrue(has(diagnostics, Diagnostic.Severity.ERROR, "target"));
        assertTrue(has(diagnostics, Diagnostic.Severity.ERROR, "source"));
    }

    @Test
    void danglingInputOutputReferencesAreErrors() {
        var spec = new PipelineSpec("dangling-refs",
                List.of(node("a")),
                List.of(),
                List.of(new PinRef("ghost", "in")), List.of(new PinRef("ghost", "out")));
        var diagnostics = SpecValidator.validate(spec);
        assertEquals(2, diagnostics.stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR).count());
        assertTrue(has(diagnostics, Diagnostic.Severity.ERROR, "unknown node 'ghost'"));
    }

    @Test
    void unknownPinsAreWarnings() {
        var spec = new PipelineSpec("unknown-pins",
                List.of(node("a"), node("b")),
                List.of(edge("a", "nope", "b", "in"), edge("a", "out", "b", "nope")),
                List.of(), List.of());
        var diagnostics = SpecValidator.validate(spec,
                Map.of("a", FORWARD, "b", FORWARD));
        assertTrue(has(diagnostics, Diagnostic.Severity.WARNING, "not a declared output"));
        assertTrue(has(diagnostics, Diagnostic.Severity.WARNING, "not a declared input"));
    }

    @Test
    void typeMismatchIsAWarningAndAnyAbsorbs() {
        var strings = new NodeContract(List.of(new Key("in", ValueType.STRING)),
                List.of(new Key("out", ValueType.STRING)));
        var numbers = new NodeContract(List.of(new Key("in", ValueType.NUMBER)),
                List.of(new Key("out", ValueType.NUMBER)));
        var anys = new NodeContract(List.of(new Key("in", ValueType.ANY)),
                List.of(new Key("out", ValueType.ANY)));
        var mismatch = new PipelineSpec("mismatch",
                List.of(node("n"), node("s")),
                List.of(edge("n", "out", "s", "in")),
                List.of(), List.of());
        assertTrue(has(SpecValidator.validate(mismatch, Map.of("n", numbers, "s", strings)),
                Diagnostic.Severity.WARNING, "type mismatch"));

        var absorbed = new PipelineSpec("absorbed",
                List.of(node("n"), node("s")),
                List.of(edge("n", "out", "s", "in")),
                List.of(), List.of());
        assertTrue(SpecValidator.validate(absorbed, Map.of("n", numbers, "s", anys)).stream()
                .noneMatch(d -> d.message().contains("type mismatch")));
    }

    @Test
    void multipleInEdgesAreAWarning() {
        var spec = new PipelineSpec("multi-in",
                List.of(node("a"), node("b"), node("c")),
                List.of(edge("a", "out", "c", "in"), edge("b", "out", "c", "in")),
                List.of(), List.of());
        var diagnostics = SpecValidator.validate(spec,
                Map.of("a", FORWARD, "b", FORWARD, "c", FORWARD));
        assertTrue(has(diagnostics, Diagnostic.Severity.WARNING, "multiple incoming edges"));
    }

    @Test
    void unsourcedReadWarnsTheNodeMayNeverFire() {
        var spec = new PipelineSpec("unsourced",
                List.of(node("a")),
                List.of(),
                List.of(), List.of());
        var diagnostics = SpecValidator.validate(spec, Map.of("a", FORWARD));
        assertTrue(has(diagnostics, Diagnostic.Severity.WARNING, "may never fire"));
    }

    @Test
    void runInputCountsAsAReadSource() {
        var spec = new PipelineSpec("sourced",
                List.of(node("a")),
                List.of(),
                List.of(new PinRef("a", "in")), List.of());
        var diagnostics = SpecValidator.validate(spec, Map.of("a", FORWARD));
        assertTrue(diagnostics.stream().noneMatch(d -> d.message().contains("may never fire")));
    }

    @Test
    void unconsumedWriteIsAWarning() {
        var spec = new PipelineSpec("unconsumed",
                List.of(node("a")),
                List.of(),
                List.of(), List.of());
        var diagnostics = SpecValidator.validate(spec, Map.of("a", FORWARD));
        assertTrue(has(diagnostics, Diagnostic.Severity.WARNING, "no consumer"));
    }

    @Test
    void declaredOutputCountsAsAWriteConsumer() {
        var spec = new PipelineSpec("consumed",
                List.of(node("a")),
                List.of(),
                List.of(), List.of(new PinRef("a", "out")));
        var diagnostics = SpecValidator.validate(spec, Map.of("a", FORWARD));
        assertTrue(diagnostics.stream().noneMatch(d -> d.message().contains("no consumer")));
    }
}
