package tech.forethought.brick.core.spi;

import java.util.Map;

/**
 * A node on the graph — the single executable unit. The engine calls nodes
 * and routes by edges; a node receives an immutable input snapshot and
 * returns a new immutable snapshot.
 *
 * <p>Implementations are discovered via {@code ServiceLoader}, must have a
 * public no-arg constructor, and must be stateless and thread-safe.
 */
public interface Node {

    /**
     * The spec type name this implementation serves (e.g. {@code "if"}).
     * Names are protocol: renaming is a breaking change.
     */
    String type();

    /**
     * Declares this node's edge-data interface (see {@link NodeContract}).
     * The engine neither consumes nor enforces the declaration; editors and
     * future diagnostics do.
     *
     * @return the contract, never null
     */
    NodeContract contract();

    /**
     * Executes this node.
     *
     * @param input   immutable snapshot of the edge data; never modified by
     *                the node
     * @param context run identity, node configuration, and service lookup
     * @return the new edge data, never null
     */
    Map<String, Object> execute(Map<String, Object> input, NodeContext context);
}
