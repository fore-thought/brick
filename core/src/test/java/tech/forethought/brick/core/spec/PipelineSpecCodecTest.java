package tech.forethought.brick.core.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineSpecCodecTest {

    private static PipelineSpec sample() {
        return new PipelineSpec("sample",
                List.of(new NodeSpec("a", "alpha", Map.of("key", "value", "retries", 1L)),
                        new NodeSpec("b", "beta", Map.of("enabled", true)),
                        new NodeSpec("c", "gamma",
                                Map.of("thresholds", List.of(1L, 2L), "nested", Map.of("x", "y")))),
                List.of(new EdgeSpec("a", "b", null), new EdgeSpec("b", "c", "go"),
                        new EdgeSpec("c", "a", "back")),
                "a", 7);
    }

    @Test
    void roundTripsThroughJson() {
        var spec = sample();
        assertEquals(spec, PipelineSpecCodec.read(PipelineSpecCodec.write(spec)));
    }

    @Test
    void roundTripsThroughMapForm() {
        var spec = sample();
        assertEquals(spec, PipelineSpecCodec.fromMap(PipelineSpecCodec.toMap(spec)));
    }

    @Test
    void writesCanonicalCompactForm() {
        var spec = new PipelineSpec("mini", List.of(new NodeSpec("only", "alpha", Map.of())),
                List.of(), "only");
        assertEquals("{\"version\":1,\"name\":\"mini\",\"entry\":\"only\","
                + "\"nodes\":[{\"id\":\"only\",\"type\":\"alpha\"}],\"edges\":[]}",
                PipelineSpecCodec.write(spec));
    }

    @Test
    @SuppressWarnings("unchecked")
    void omitsDefaultedFieldsInMapForm() {
        var map = PipelineSpecCodec.toMap(new PipelineSpec("mini",
                List.of(new NodeSpec("only", "alpha", Map.of())),
                List.of(new EdgeSpec("only", "only", null)), "only"));
        assertEquals(PipelineSpecCodec.FORMAT_VERSION, map.get("version"));
        assertFalse(map.containsKey("maxIterations"));
        var nodes = (List<Map<String, Object>>) map.get("nodes");
        assertFalse(nodes.get(0).containsKey("config"));
        var edges = (List<Map<String, Object>>) map.get("edges");
        assertFalse(edges.get(0).containsKey("label"));
    }

    @Test
    void appliesDefaultsForAbsentFields() {
        var spec = PipelineSpecCodec.read("""
                {"version": 1, "name": "mini", "entry": "only",
                 "nodes": [{"id": "only", "type": "alpha"}]}
                """);
        assertEquals(PipelineSpec.DEFAULT_MAX_ITERATIONS, spec.maxIterations());
        assertEquals(Map.of(), spec.nodes().get(0).config());
        assertEquals(List.of(), spec.edges());
    }

    @Test
    void normalizesJsonNumbers() {
        var spec = PipelineSpecCodec.read("""
                {"version": 1, "name": "n", "entry": "a", "maxIterations": 9,
                 "nodes": [{"id": "a", "type": "t", "config": {"count": 1, "ratio": 0.5}}]}
                """);
        assertEquals(9, spec.maxIterations());
        assertInstanceOf(Long.class, spec.nodes().get(0).config().get("count"));
        assertInstanceOf(Double.class, spec.nodes().get(0).config().get("ratio"));
    }

    @Test
    void readsNullLabelAsDefaultEdge() {
        var spec = PipelineSpecCodec.read("""
                {"version": 1, "name": "n", "entry": "a",
                 "nodes": [{"id": "a", "type": "t"}, {"id": "b", "type": "t"}],
                 "edges": [{"from": "a", "to": "b", "label": null}]}
                """);
        assertNull(spec.edges().get(0).label());
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 2, "name": "n", "entry": "a", "nodes": []}
                """));
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 1, "entry": "a", "nodes": []}
                """));
    }

    @Test
    void rejectsMistypedField() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 1, "name": "n", "entry": "a", "nodes": {}}
                """));
    }

    @Test
    void rejectsNonObjectTopLevel() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("[]"));
    }
}
