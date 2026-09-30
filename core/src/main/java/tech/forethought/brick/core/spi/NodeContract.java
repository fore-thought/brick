package tech.forethought.brick.core.spi;

import java.util.List;

/**
 * A node's declared edge-data interface: which keys it reads, which keys it
 * introduces or overwrites, and the value type family of each. The
 * declaration is an annotation layer for editors and future diagnostics —
 * the engine neither consumes nor enforces it, and a declaration that
 * mismatches runtime reality is not an error.
 *
 * <p>The declaration states the semantic interface, not the implementation
 * mechanism: keys a node merely passes through unchanged are neither reads
 * nor writes, because whole-container copy-then-append is the platform's
 * replacement semantics, not a node's interface. When {@code dynamic} is
 * true the key set cannot be statically enumerated (e.g. the names come
 * from node configuration or an external file); {@code reads} and
 * {@code writes} may still carry the known fixed part so tools can mark
 * the rest as dynamic. Immutable.
 *
 * @param reads   keys the node consumes from edge data, in declaration order
 * @param writes  keys the node introduces or overwrites on edge data
 * @param dynamic true if the key set is known only at run time
 */
public record NodeContract(List<Key> reads, List<Key> writes, boolean dynamic) {

    public NodeContract {
        reads = List.copyOf(reads);
        writes = List.copyOf(writes);
    }

    /**
     * One edge-data key of a {@link NodeContract} and its value type family.
     * Immutable.
     *
     * @param name the key name as it appears on edge data
     * @param type the family the key's values belong to
     */
    public record Key(String name, ValueType type) {
    }

    /**
     * Value type families of edge data. The families are coarse on purpose:
     * the declaration annotates, it does not type-check.
     */
    public enum ValueType {

        /** A string. */
        STRING,

        /** A boolean. */
        BOOLEAN,

        /** Any number. */
        NUMBER,

        /** A list. */
        LIST,

        /** A map. */
        MAP,

        /** Anything else: domain records, mixed or unknown shapes. */
        ANY
    }

    /** No reads, no writes (e.g. a pure passthrough node). */
    public static NodeContract empty() {
        return new NodeContract(List.of(), List.of(), false);
    }

    /**
     * Reads and writes known only at run time (e.g. a node injecting
     * entries whose names come from a file).
     */
    public static NodeContract dynamicKeys() {
        return new NodeContract(List.of(), List.of(), true);
    }
}
