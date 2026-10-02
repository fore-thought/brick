package tech.forethought.brick.nodes.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.bootstrap.Bootstrap;
import tech.forethought.brick.core.spi.Node;

/**
 * Verifies that all default-chain nodes (plus the basic pack on the
 * classpath) are discoverable through ServiceLoader declarations.
 */
class NodeDiscoveryTest {

    @Test
    void discoversAllDefaultChainNodes() {
        var services = Bootstrap.discover();
        for (var type : new String[] {"input", "context-preprocess", "convert-out", "llm-call",
                "convert-in", "tool-exec", "output", "branch", "loop", "pass",
                "config-loader"}) {
            assertEquals(type, services.require(Node.class, type).type());
        }
    }
}
