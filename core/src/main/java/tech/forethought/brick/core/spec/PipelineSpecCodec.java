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
    public static final int FORMAT_VERSION = 1;

    private PipelineSpecCodec() {
    }

    /** Converts a spec to its map form; fields at their default are omitted. */
    public static Map<String, Object> toMap(PipelineSpec spec) {
        var map = new LinkedHashMap<String, Object>();
        map.put("version", FORMAT_VERSION);
        map.put("name", spec.name());
        map.put("entry", spec.entryNodeId());
        if (spec.maxIterations() != PipelineSpec.DEFAULT_MAX_ITERATIONS) {
            map.put("maxIterations", spec.maxIterations());
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
        var entry = requireString(map, "entry", "$");
        var maxIterations = PipelineSpec.DEFAULT_MAX_ITERATIONS;
        if (map.get("maxIterations") != null) {
            maxIterations = requireNumber(map, "maxIterations", "$").intValue();
        }
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
        return new PipelineSpec(name, nodes, edges, entry, maxIterations);
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
        map.put("from", edge.from());
        map.put("to", edge.to());
        if (edge.label() != null) {
            map.put("label", edge.label());
        }
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
        var from = requireString(map, "from", path);
        var to = requireString(map, "to", path);
        var labelValue = map.get("label");
        if (labelValue != null && !(labelValue instanceof String)) {
            throw error(path, "field 'label' must be a string");
        }
        return new EdgeSpec(from, to, (String) labelValue);
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
