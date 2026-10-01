package tech.forethought.brick.core.spi;

import java.util.List;

/**
 * A node's declared pin interface: which pins it reads, which pins it
 * consumes as bound-once context, which pins it writes, and the value type
 * family of each. The engine consumes the declaration to derive triggering:
 * all reads and context pins must carry a binding before the node fires, and
 * a new value on any <em>read</em> re-fires it — context pins are sticky
 * companions that never trigger by themselves (loop-carried conversations,
 * configuration injected once). Editors use the declaration for drawing and
 * diagnostics; a declaration that mismatches runtime reality is not an
 * error.
 *
 * <p>Pin names are node-local. When {@code dynamic} is true the pin set
 * cannot be statically enumerated (e.g. the names come from node
 * configuration or an external file); {@code reads}, {@code context} and
 * {@code writes} may still carry the known fixed part so tools can mark the
 * rest as dynamic. Immutable.
 *
 * @param reads   triggering pins: bound required, a new value re-fires
 * @param context non-triggering pins: bound required, never re-fire
 * @param writes  pins the node produces
 * @param dynamic true if the pin set is known only at run time
 */
public record NodeContract(List<Key> reads, List<Key> context, List<Key> writes,
                           boolean dynamic) {

    public NodeContract {
        reads = List.copyOf(reads);
        context = List.copyOf(context);
        writes = List.copyOf(writes);
    }

    public NodeContract(List<Key> reads, List<Key> writes, boolean dynamic) {
        this(reads, List.of(), writes, dynamic);
    }

    /**
     * One pin of a {@link NodeContract} and its value type family. Immutable.
     *
     * @param name the pin name, local to the node
     * @param type the family the pin's values belong to
     */
    public record Key(String name, ValueType type) {
    }

    /**
     * Value type families of pin values. The families are coarse on purpose:
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

    /** No reads, no writes (e.g. a source node producing nothing). */
    public static NodeContract empty() {
        return new NodeContract(List.of(), List.of(), List.of(), false);
    }

    /**
     * Reads and writes known only at run time (e.g. a node producing pins
     * whose names come from configuration).
     */
    public static NodeContract dynamicKeys() {
        return new NodeContract(List.of(), List.of(), List.of(), true);
    }
}
