package tech.forethought.brick.core.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.ServiceLoader;
import tech.forethought.brick.core.event.EventListener;
import tech.forethought.brick.core.spi.Node;
import tech.forethought.brick.core.spi.ProtocolAdapter;
import tech.forethought.brick.core.spi.Services;
import tech.forethought.brick.core.spi.Tool;

/**
 * Assembly: discovers SPI implementations through {@link ServiceLoader} and
 * exposes them as {@link Services}. Pure discovery and selection; no
 * business logic.
 *
 * <p>Hot-plug: every {@code .jar} under an extensions directory gets its own
 * {@link ExtensionClassLoader} (parented on the platform loader), so
 * extension implementations stay replaceable deployment units. Duplicate
 * names across loaders fail fast.
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
        registerAll(registry, loader);
        registry.freeze();
        return registry;
    }

    /**
     * Discovers implementations on the context class loader plus every
     * {@code .jar} in {@code extensionsDir} (missing directory is tolerated).
     * Extension loaders stay open until {@link Services#close()}.
     */
    public static Services discover(Path extensionsDir) {
        return discover(Thread.currentThread().getContextClassLoader(), extensionsDir);
    }

    /** See {@link #discover(Path)}. */
    public static Services discover(ClassLoader platformLoader, Path extensionsDir) {
        var registry = new ServiceRegistry();
        registerAll(registry, platformLoader);
        if (Files.isDirectory(extensionsDir)) {
            var created = new ArrayList<URLClassLoader>();
            try (var entries = Files.list(extensionsDir)) {
                for (var jar : entries
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .toList()) {
                    var loader = new ExtensionClassLoader(
                            new URL[] {jar.toUri().toURL()}, platformLoader);
                    created.add(loader);
                    registerAll(registry, loader);
                }
            } catch (IOException | RuntimeException e) {
                for (var loader : created) {
                    try {
                        loader.close();
                    } catch (IOException suppressed) {
                        // best effort
                    }
                }
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("cannot scan extensions dir " + extensionsDir,
                        e);
            }
            created.forEach(registry::addExtensionLoader);
        }
        registry.freeze();
        return registry;
    }

    private static void registerAll(ServiceRegistry registry, ClassLoader loader) {
        for (var node : ServiceLoader.load(Node.class, loader)) {
            registry.register(Node.class, node.type(), node);
        }
        for (var adapter : ServiceLoader.load(ProtocolAdapter.class, loader)) {
            registry.register(ProtocolAdapter.class, adapter.name(), adapter);
        }
        for (var tool : ServiceLoader.load(Tool.class, loader)) {
            registry.register(Tool.class, tool.definition().name(), tool);
        }
        for (var listener : ServiceLoader.load(EventListener.class, loader)) {
            registry.register(EventListener.class, listener.getClass().getName(), listener);
        }
    }

    /**
     * Loader for one extension jar: service declarations are only read from
     * its own jar (never re-discovered through the parent), while classes
     * still delegate parent-first.
     */
    static final class ExtensionClassLoader extends URLClassLoader {

        ExtensionClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name.startsWith("META-INF/services/")) {
                return findResources(name);
            }
            return super.getResources(name);
        }
    }
}
