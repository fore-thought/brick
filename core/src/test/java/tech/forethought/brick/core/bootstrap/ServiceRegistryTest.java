package tech.forethought.brick.core.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.mock.EchoNode;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.Tool;

class ServiceRegistryTest {

    @Test
    void frozenRegistryServesLookups() {
        var registry = new ServiceRegistry();
        var node = new EchoNode();
        registry.register(Node.class, node.type(), node);
        registry.freeze();
        assertSame(node, registry.require(Node.class, "echo"));
        assertEquals(List.of(node), registry.all(Node.class));
        assertTrue(registry.all(Tool.class).isEmpty());
    }

    @Test
    void registrationsAfterFreezeFail() {
        var registry = new ServiceRegistry();
        registry.freeze();
        var e = assertThrows(IllegalStateException.class,
                () -> registry.register(Node.class, "echo", new EchoNode()));
        assertTrue(e.getMessage().contains("frozen"));
        assertThrows(IllegalStateException.class, () -> registry.addExtensionLoader(null));
        assertThrows(IllegalStateException.class, registry::freeze);
    }

    @Test
    void lookupsBeforeFreezeFail() {
        var registry = new ServiceRegistry();
        var e = assertThrows(IllegalStateException.class, () -> registry.require(Node.class, "echo"));
        assertTrue(e.getMessage().contains("not frozen"));
        assertThrows(IllegalStateException.class, () -> registry.all(Node.class));
        assertThrows(IllegalStateException.class, registry::close);
    }

    @Test
    void frozenRegistryServesConcurrentLookups() throws ExecutionException, InterruptedException {
        var registry = new ServiceRegistry();
        registry.register(Node.class, "echo", new EchoNode());
        registry.freeze();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, 64)
                    .mapToObj(i -> executor.submit(() -> registry.require(Node.class, "echo").type()))
                    .toList();
            for (var future : futures) {
                assertEquals("echo", future.get());
            }
        }
    }
}
