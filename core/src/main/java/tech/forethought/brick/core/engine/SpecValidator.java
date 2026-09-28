package tech.forethought.brick.core.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import tech.forethought.brick.core.spec.PipelineSpec;

/**
 * Pure structural checks over a spec. Produces diagnostics and never throws;
 * following the "diagnostics, not gatekeepers" philosophy, callers decide
 * what to block on.
 */
public final class SpecValidator {

    private SpecValidator() {
    }

    /**
     * Validates the spec's structure: duplicate node ids, entry existence,
     * dangling edge references (errors); unreachable nodes (warnings).
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
        if (!ids.contains(spec.entryNodeId())) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                    "entry '" + spec.entryNodeId() + "'", "entry node does not exist"));
        }
        for (var edge : spec.edges()) {
            if (!ids.contains(edge.from())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "edge '" + edge.from() + "' -> '" + edge.to() + "'",
                        "source node does not exist"));
            }
            if (!ids.contains(edge.to())) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                        "edge '" + edge.from() + "' -> '" + edge.to() + "'",
                        "target node does not exist"));
            }
        }

        if (ids.contains(spec.entryNodeId())) {
            for (var id : unreachable(spec)) {
                diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING,
                        "node '" + id + "'", "unreachable from entry"));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static List<String> unreachable(PipelineSpec spec) {
        var reachable = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(spec.entryNodeId());
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (reachable.add(current)) {
                for (var edge : spec.edges()) {
                    if (edge.from().equals(current)) {
                        queue.add(edge.to());
                    }
                }
            }
        }
        return spec.nodes().stream().map(n -> n.id()).filter(id -> !reachable.contains(id)).toList();
    }
}
