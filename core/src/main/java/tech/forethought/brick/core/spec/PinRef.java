package tech.forethought.brick.core.spec;

/**
 * A pin: one node's named port. Pins are the endpoints of edges and the
 * declaration points of a spec's external run interface ({@code inputs} /
 * {@code outputs}). Pin names are node-local — wiring translates between
 * them (node A's {@code x} may feed node B's {@code u}). Immutable.
 *
 * @param node the node id, unique within one spec
 * @param key  the pin name, local to that node
 */
public record PinRef(String node, String key) {
}
