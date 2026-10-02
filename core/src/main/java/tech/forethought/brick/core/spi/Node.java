package tech.forethought.brick.core.spi;

import java.util.Map;

/**
 * A node on the graph — the single executable unit. The engine fires a node
 * when its declared reads all carry a binding; the node receives an
 * immutable snapshot of those bindings and returns only the keys it
 * produces, which the engine delivers along the node's out-edges.
 *
 * <p>Implementations are discovered via {@code ServiceLoader}, must have a
 * public no-arg constructor, and must be stateless and thread-safe.
 */
public interface Node {

    /**
     * The spec type name this implementation serves (e.g. {@code "branch"}).
     * Names are protocol: renaming is a breaking change.
     */
    String type();

    /**
     * Declares this node's pin interface (see {@link NodeContract}). The
     * engine consumes {@code reads} to derive triggering; editors and
     * diagnostics consume the rest. Implementations whose pins do not depend
     * on configuration ignore the parameter.
     *
     * @param config the node's configuration from the spec
     * @return the contract, never null
     */
    NodeContract contract(Map<String, Object> config);

    /**
     * Executes this node.
     *
     * @param input   immutable snapshot of this node's input bindings (its
     *                contract reads); never modified by the node
     * @param context run identity, node configuration, and service lookup
     * @return the produced key-value pairs, never null; only these keys are
     *         delivered onward
     */
    Map<String, Object> execute(Map<String, Object> input, NodeContext context);
}
