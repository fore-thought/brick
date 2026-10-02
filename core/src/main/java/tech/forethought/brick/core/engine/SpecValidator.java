package tech.forethought.brick.core.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tech.forethought.brick.core.spec.EdgeSpec;
import tech.forethought.brick.core.spec.PinRef;
import tech.forethought.brick.core.spec.PipelineSpec;
import tech.forethought.brick.core.spi.NodeContract;

/**
 * Pure checks over a spec. Produces diagnostics and never throws; following
 * the "diagnostics, not gatekeepers" philosophy, callers decide what to
 * block on. Thread-safe (stateless).
 */
public final class SpecValidator {

    private SpecValidator() {
    }

    /**
     * Validates the spec's structure: duplicate node ids, edge and
     * inputs/outputs references to unknown nodes (errors). This is the
     * pre-run gate; pin-level concerns are {@link #validate(PipelineSpec,
     * Map)} and never block a run.
     */
    public static List<Diagnostic> validate(PipelineSpec spec) {
        var diagnostics = new ArrayList<Diagnostic>();

        var ids = new HashSet<String>();
        for (var node : spec.nodes()) {
            if (!ids.add(node.id())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "node '" + node.id() + "'", "duplicate node id"));
            }
        }
        for (var edge : spec.edges()) {
            if (!ids.contains(edge.from().node())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "edge '" + describe(edge) + "'", "source node does not exist"));
            }
            if (!ids.contains(edge.to().node())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "edge '" + describe(edge) + "'", "target node does not exist"));
            }
        }
        for (var pin : spec.inputs()) {
            if (!ids.contains(pin.node())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "input '" + pin.node() + "." + pin.key() + "'",
                        "references unknown node '" + pin.node() + "'"));
            }
        }
        for (var pin : spec.outputs()) {
            if (!ids.contains(pin.node())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "output '" + pin.node() + "." + pin.key() + "'",
                        "references unknown node '" + pin.node() + "'"));
            }
        }
        return List.copyOf(diagnostics);
    }

    /**
     * Structural validation plus pin-level checks against the given node
     * contracts (node id to contract). All pin-level findings are warnings —
     * drafts stay saveable and graphs stay runnable:
     *
     * <ul>
     *   <li>unknown pins on either end of an edge (skipped for dynamic
     *       contracts, whose pin set is known only at run time);</li>
     *   <li>type-family mismatches along an edge (ANY absorbs everything);</li>
     *   <li>multiple edges into one input pin (legal loop-back mechanism,
     *       also a typo magnet);</li>
     *   <li>reads with no source — the node may never fire;</li>
     *   <li>writes with no consumer.</li>
     * </ul>
     *
     * Nodes without a contract in the map get no pin-level checks.
     */
    public static List<Diagnostic> validate(PipelineSpec spec, Map<String, NodeContract> contracts) {
        var diagnostics = new ArrayList<Diagnostic>(validate(spec));

        var inEdgeCounts = new HashMap<PinRef, Integer>();
        var sourcedPins = new HashSet<PinRef>();
        var consumedPins = new HashSet<PinRef>();
        for (var edge : spec.edges()) {
            inEdgeCounts.merge(edge.to(), 1, Integer::sum);
            sourcedPins.add(edge.to());
            consumedPins.add(edge.from());
            var fromType = checkPin(contracts.get(edge.from().node()), edge.from(), false,
                    diagnostics);
            var toType = checkPin(contracts.get(edge.to().node()), edge.to(), true, diagnostics);
            if (fromType != null && toType != null && !compatible(fromType, toType)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                        "edge '" + describe(edge) + "'",
                        "type mismatch: " + edge.from().key() + " (" + fromType + ") -> "
                                + edge.to().key() + " (" + toType + ")"));
            }
        }
        spec.inputs().forEach(sourcedPins::add);
        spec.outputs().forEach(consumedPins::add);

        for (var entry : inEdgeCounts.entrySet()) {
            if (entry.getValue() > 1) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                        "pin '" + describe(entry.getKey()) + "'",
                        "has multiple incoming edges (intentional loop-back or typo?)"));
            }
        }
        for (var node : spec.nodes()) {
            var contract = contracts.get(node.id());
            if (contract == null) {
                continue;
            }
            for (var read : contract.reads()) {
                var pin = new PinRef(node.id(), read.name());
                if (!sourcedPins.contains(pin)) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                            "pin '" + describe(pin) + "'",
                            "has no source; node '" + node.id() + "' may never fire"));
                }
            }
            for (var write : contract.writes()) {
                var pin = new PinRef(node.id(), write.name());
                if (!consumedPins.contains(pin)) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                            "pin '" + describe(pin) + "'", "has no consumer"));
                }
            }
        }
        return List.copyOf(diagnostics);
    }

    /**
     * The declared type family of a pin, or null if it cannot be determined
     * (no contract, or a dynamic contract that does not declare the pin —
     * never a finding). A declared-but-missing pin on a static contract is a
     * warning and also yields null.
     */
    private static NodeContract.ValueType checkPin(NodeContract contract, PinRef pin,
                                                   boolean read, List<Diagnostic> diagnostics) {
        if (contract == null) {
            return null;
        }
        var declared = read ? contract.reads() : contract.writes();
        for (var key : declared) {
            if (key.name().equals(pin.key())) {
                return key.type();
            }
        }
        if (!contract.dynamic()) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                    "pin '" + describe(pin) + "'",
                    read ? "is not a declared input of node '" + pin.node() + "'"
                            : "is not a declared output of node '" + pin.node() + "'"));
        }
        return null;
    }

    private static boolean compatible(NodeContract.ValueType from, NodeContract.ValueType to) {
        return from == NodeContract.ValueType.ANY || to == NodeContract.ValueType.ANY
                || from == to;
    }

    private static String describe(EdgeSpec edge) {
        return describe(edge.from()) + " -> " + describe(edge.to());
    }

    private static String describe(PinRef pin) {
        return pin.node() + "." + pin.key();
    }
}
