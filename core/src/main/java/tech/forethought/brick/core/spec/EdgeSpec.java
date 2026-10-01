package tech.forethought.brick.core.spec;

/**
 * A data wire: every value the source node writes to {@code from.key} is
 * delivered (sticky, later writes overwrite earlier ones) to {@code to.key}
 * of the target node. Execution order is derived from these data
 * dependencies; the edge itself carries no routing logic. Immutable.
 *
 * @param from the producing pin
 * @param to   the consuming pin
 */
public record EdgeSpec(PinRef from, PinRef to) {
}
