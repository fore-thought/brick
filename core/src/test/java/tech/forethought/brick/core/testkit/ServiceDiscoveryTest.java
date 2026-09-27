package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.TextTransformer;

/**
 * Verifies that both toy implementations are discoverable through
 * ServiceLoader via the META-INF/services declaration.
 */
final class ServiceDiscoveryTest {

    @Test
    void discoversBothDeclaredImplementations() {
        var names = new TreeSet<String>();
        for (TextTransformer transformer : ServiceLoader.load(TextTransformer.class)) {
            names.add(transformer.name());
        }
        assertEquals(Set.of("reverse", "upper"), names);
    }
}
