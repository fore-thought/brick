package tech.forethought.brick.core.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.forethought.brick.core.util.Json;

/**
 * Maps between {@link PipelineSpec} records and their portable map form, and
 * serializes that form to and from JSON — the canonical pipeline format.
 * Structural problems (malformed JSON, missing or mistyped fields, an unknown
 * format version) fail fast with a located error; semantic concerns are left
 * to {@code SpecValidator}, following the "diagnostics, not gatekeepers"
 * philosophy. Thread-safe (stateless).
 */
public final class PipelineSpecCodec {

    /** The format version this codec reads and writes. */
    public static final int FORMAT_VERSION = 2;

    private PipelineSpecCodec() {
    }

    /** Converts a spec to its map form; fields at their default are omitted. */
    public static Map<String, Object> toMap(PipelineSpec spec) {
        var map = new LinkedHashMap<String, Object>();
        map.put("version", FORMAT_VERSION);
        map.put("name", spec.name());
        if (spec.maxFirings() != PipelineSpec.DEFAULT_MAX_FIRINGS) {
            map.put("maxFirings", spec.maxFirings());
        }
        if (!spec.inputs().isEmpty()) {
            map.put("inputs", spec.inputs().stream().map(PipelineSpecCodec::pinToMap).toList());
        }
        if (!spec.outputs().isEmpty()) {
            map.put("outputs", spec.outputs().stream().map(PipelineSpecCodec::pinToMap).toList());
        }
        map.put("nodes", spec.nodes().stream().map(PipelineSpecCodec::nodeToMap).toList());
        map.put("edges", spec.edges().stream().map(PipelineSpecCodec::edgeToMap).toList());
        return map;
    }

    /**
     * Parses the map form back into a spec. Absent (or null) optional fields
     * take their defaults; values pass through as-is, so map-form callers keep
     * whatever {@code Number} types they supplied.
     */
    public static PipelineSpec fromMap(Map<String, Object> map) {
        var version = requireNumber(map, "version", "$");
        if (version.doubleValue() != FORMAT_VERSION) {
            throw error("$", "unsupported format version " + version);
        }
        var name = requireString(map, "name", "$");
        var maxFirings = PipelineSpec.DEFAULT_MAX_FIRINGS;
        if (map.get("maxFirings") != null) {
            maxFirings = requireNumber(map, "maxFirings", "$").intValue();
        }
        var inputs = pinListFromMap(map.get("inputs"), "$.inputs");
        var outputs = pinListFromMap(map.get("outputs"), "$.outputs");
        var nodes = new ArrayList<NodeSpec>();
        var index = 0;
        for (var element : requireList(map, "nodes", "$")) {
            var path = "$.nodes[" + index + "]";
            nodes.add(nodeFromMap(requireObject(element, path), path));
            index++;
        }
        var edges = new ArrayList<EdgeSpec>();
        if (map.get("edges") != null) {
            index = 0;
            for (var element : asList(map.get("edges"), "$.edges")) {
                var path = "$.edges[" + index + "]";
                edges.add(edgeFromMap(requireObject(element, path), path));
                index++;
            }
        }
        return new PipelineSpec(name, nodes, edges, inputs, outputs, maxFirings);
    }

    /** Serializes a spec to compact JSON text. */
    public static String write(PipelineSpec spec) {
        return Json.write(toMap(spec));
    }

    /**
     * Parses JSON text into a spec. Numbers normalize to {@code Long} or
     * {@code Double}, following JSON semantics.
     */
    public static PipelineSpec read(String text) {
        return fromMap(requireObject(Json.read(text), "$"));
    }

    private static Map<String, Object> nodeToMap(NodeSpec node) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", node.id());
        map.put("type", node.type());
        if (!node.config().isEmpty()) {
            map.put("config", node.config());
        }
        return map;
    }

    private static Map<String, Object> edgeToMap(EdgeSpec edge) {
        var map = new LinkedHashMap<String, Object>();
        map.put("from", pinToMap(edge.from()));
        map.put("to", pinToMap(edge.to()));
        return map;
    }

    private static Map<String, Object> pinToMap(PinRef pin) {
        var map = new LinkedHashMap<String, Object>();
        map.put("node", pin.node());
        map.put("key", pin.key());
        return map;
    }

    private static NodeSpec nodeFromMap(Map<String, Object> map, String path) {
        var id = requireString(map, "id", path);
        var type = requireString(map, "type", path);
        var config = Map.<String, Object>of();
        if (map.get("config") != null) {
            config = requireObject(map.get("config"), path + ".config");
        }
        return new NodeSpec(id, type, config);
    }

    private static EdgeSpec edgeFromMap(Map<String, Object> map, String path) {
        var from = pinFromMap(map.get("from"), path + ".from");
        var to = pinFromMap(map.get("to"), path + ".to");
        return new EdgeSpec(from, to);
    }

    private static PinRef pinFromMap(Object value, String path) {
        var map = requireObject(value, path);
        return new PinRef(requireString(map, "node", path), requireString(map, "key", path));
    }

    private static List<PinRef> pinListFromMap(Object value, String path) {
        var pins = new ArrayList<PinRef>();
        if (value == null) {
            return List.copyOf(pins);
        }
        var index = 0;
        for (var element : asList(value, path)) {
            pins.add(pinFromMap(element, path + "[" + index + "]"));
            index++;
        }
        return List.copyOf(pins);
    }

    private static String requireString(Map<String, Object> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw error(path, "missing field '" + key + "'");
        }
        if (!(map.get(key) instanceof String value)) {
            throw error(path, "field '" + key + "' must be a string");
        }
        return value;
    }

    private static Number requireNumber(Map<String, Object> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw error(path, "missing field '" + key + "'");
        }
        if (!(map.get(key) instanceof Number value)) {
            throw error(path, "field '" + key + "' must be a number");
        }
        return value;
    }

    private static List<?> requireList(Map<String, Object> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw error(path, "missing field '" + key + "'");
        }
        return asList(map.get(key), path + "." + key);
    }

    private static List<?> asList(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw error(path, "must be an array");
        }
        return list;
    }

    private static Map<String, Object> requireObject(Object value, String path) {
        if (!(value instanceof Map<?, ?>)) {
            throw error(path, "must be an object");
        }
        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) value;
        return map;
    }

    private static IllegalArgumentException error(String path, String detail) {
        return new IllegalArgumentException("invalid pipeline format at " + path + ": " + detail);
    }
}
