package tech.forethought.brick.core.bootstrap;

import java.util.ServiceLoader;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.spi.Services;
import tech.forethought.brick.core.spi.Tool;

/**
 * Assembly: discovers SPI implementations through {@link ServiceLoader} and
 * exposes them as {@link Services}. Pure discovery and selection; no
 * business logic.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    /** Discovers implementations on the current thread's context class loader. */
    public static Services discover() {
        return discover(Thread.currentThread().getContextClassLoader());
    }

    /** Discovers implementations on the given class loader. */
    public static Services discover(ClassLoader loader) {
        var registry = new ServiceRegistry();
        for (var node : ServiceLoader.load(Node.class, loader)) {
            registry.register(Node.class, node.type(), node);
        }
        for (var adapter : ServiceLoader.load(ProtocolAdapter.class, loader)) {
            registry.register(ProtocolAdapter.class, adapter.name(), adapter);
        }
        for (var tool : ServiceLoader.load(Tool.class, loader)) {
            registry.register(Tool.class, tool.definition().name(), tool);
        }
        return registry;
    }
}
