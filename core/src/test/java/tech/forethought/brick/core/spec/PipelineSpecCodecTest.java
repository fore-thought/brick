package tech.forethought.brick.core.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineSpecCodecTest {

    private static EdgeSpec edge(String fromNode, String fromKey, String toNode, String toKey) {
        return new EdgeSpec(new PinRef(fromNode, fromKey), new PinRef(toNode, toKey));
    }

    private static PipelineSpec sample() {
        return new PipelineSpec("sample",
                List.of(new NodeSpec("a", "alpha", Map.of("key", "value", "retries", 1L)),
                        new NodeSpec("b", "beta", Map.of("enabled", true)),
                        new NodeSpec("c", "gamma",
                                Map.of("thresholds", List.of(1L, 2L), "nested", Map.of("x", "y")))),
                List.of(edge("a", "out", "b", "in"), edge("b", "out", "c", "in"),
                        edge("c", "out", "a", "in")),
                List.of(new PinRef("a", "in")), List.of(new PinRef("c", "out")), 7);
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
                List.of(), List.of(), List.of());
        assertEquals("{\"version\":2,\"name\":\"mini\","
                        + "\"nodes\":[{\"id\":\"only\",\"type\":\"alpha\"}],\"edges\":[]}",
                PipelineSpecCodec.write(spec));
    }

    @Test
    void writesObjectFormEdges() {
        var spec = new PipelineSpec("mini", List.of(new NodeSpec("a", "alpha", Map.of()),
                new NodeSpec("b", "beta", Map.of())),
                List.of(edge("a", "out", "b", "in")), List.of(), List.of());
        assertEquals("{\"version\":2,\"name\":\"mini\",\"nodes\":["
                        + "{\"id\":\"a\",\"type\":\"alpha\"},{\"id\":\"b\",\"type\":\"beta\"}],"
                        + "\"edges\":[{\"from\":{\"node\":\"a\",\"key\":\"out\"},"
                        + "\"to\":{\"node\":\"b\",\"key\":\"in\"}}]}",
                PipelineSpecCodec.write(spec));
    }

    @Test
    @SuppressWarnings("unchecked")
    void omitsDefaultedFieldsInMapForm() {
        var map = PipelineSpecCodec.toMap(new PipelineSpec("mini",
                List.of(new NodeSpec("only", "alpha", Map.of())),
                List.of(edge("only", "out", "only", "in")),
                List.of(), List.of()));
        assertEquals(PipelineSpecCodec.FORMAT_VERSION, map.get("version"));
        assertFalse(map.containsKey("maxFirings"));
        assertFalse(map.containsKey("inputs"));
        assertFalse(map.containsKey("outputs"));
        var nodes = (List<Map<String, Object>>) map.get("nodes");
        assertFalse(nodes.get(0).containsKey("config"));
        var edges = (List<Map<String, Object>>) map.get("edges");
        var edge = edges.get(0);
        assertEquals(Map.of("node", "only", "key", "out"), edge.get("from"));
        assertEquals(Map.of("node", "only", "key", "in"), edge.get("to"));
    }

    @Test
    void appliesDefaultsForAbsentFields() {
        var spec = PipelineSpecCodec.read("""
                {"version": 2, "name": "mini",
                 "nodes": [{"id": "only", "type": "alpha"}]}
                """);
        assertEquals(PipelineSpec.DEFAULT_MAX_FIRINGS, spec.maxFirings());
        assertEquals(Map.of(), spec.nodes().get(0).config());
        assertEquals(List.of(), spec.edges());
        assertEquals(List.of(), spec.inputs());
        assertEquals(List.of(), spec.outputs());
    }

    @Test
    void readsExplicitNullInputsOutputs() {
        var spec = PipelineSpecCodec.read("""
                {"version": 2, "name": "mini", "inputs": null, "outputs": null,
                 "nodes": [{"id": "only", "type": "alpha"}]}
                """);
        assertEquals(List.of(), spec.inputs());
        assertEquals(List.of(), spec.outputs());
    }

    @Test
    void normalizesJsonNumbers() {
        var spec = PipelineSpecCodec.read("""
                {"version": 2, "name": "n", "maxFirings": 9,
                 "nodes": [{"id": "a", "type": "t", "config": {"count": 1, "ratio": 0.5}}]}
                """);
        assertEquals(9, spec.maxFirings());
        assertInstanceOf(Long.class, spec.nodes().get(0).config().get("count"));
        assertInstanceOf(Double.class, spec.nodes().get(0).config().get("ratio"));
    }

    @Test
    void rejectsRetiredVersion1() {
        var e = assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 1, "name": "n", "entry": "a", "nodes": []}
                """));
        assertEquals("invalid pipeline format at $: unsupported format version 1",
                e.getMessage());
    }

    @Test
    void rejectsUnknownFutureVersion() {
        var e = assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 3, "name": "n", "nodes": []}
                """));
        assertEquals("invalid pipeline format at $: unsupported format version 3",
                e.getMessage());
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 2, "nodes": []}
                """));
    }

    @Test
    void rejectsMistypedField() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 2, "name": "n", "nodes": {}}
                """));
    }

    @Test
    void rejectsEdgeWithoutPinObjects() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("""
                {"version": 2, "name": "n",
                 "nodes": [{"id": "a", "type": "t"}, {"id": "b", "type": "t"}],
                 "edges": [{"from": "a", "to": "b"}]}
                """));
    }

    @Test
    void rejectsNonObjectTopLevel() {
        assertThrows(IllegalArgumentException.class, () -> PipelineSpecCodec.read("[]"));
    }
}
