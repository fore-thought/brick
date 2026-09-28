package tech.forethought.brick.core.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.spi.Tool;

class BootstrapTest {

    @Test
    void discoversDeclaredImplementations() {
        var services = Bootstrap.discover();
        assertEquals("echo", services.require(Node.class, "echo").type());
        assertEquals("mock", services.require(ProtocolAdapter.class, "mock").name());
        assertEquals("echo", services.require(Tool.class, "echo").definition().name());
    }

    @Test
    void missingImplementationFailsWithPreciseMessage() {
        var services = Bootstrap.discover();
        var e = assertThrows(IllegalStateException.class,
                () -> services.require(ProtocolAdapter.class, "nonexistent"));
        assertTrue(e.getMessage().contains("nonexistent"));
        assertTrue(e.getMessage().contains("mock"));
    }
}
