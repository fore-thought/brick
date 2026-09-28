package tech.forethought.brick.core.spec;

/**
 * A directed edge between two nodes. A null label marks the default edge;
 * labeled edges are matched against the route value left by gateway nodes.
 * Immutable.
 *
 * @param from  source node id
 * @param to    target node id
 * @param label route label, or null for the default edge
 */
public record EdgeSpec(String from, String to, String label) {
}
