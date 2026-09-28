package tech.forethought.brick.core.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.forethought.brick.core.mock.PlugNode;
import tech.forethought.brick.core.mock.RogueEchoNode;
import tech.forethought.brick.core.spi.Node;

class HotPlugTest {

    @TempDir
    Path extensionsDir;

    @Test
    void loadsNodeFromExtensionJar() throws IOException {
        jarWithNodeImpl(PlugNode.class);
        var services = Bootstrap.discover(extensionsDir);
        assertEquals("plug", services.require(Node.class, "plug").type());
        // platform implementations keep working alongside
        assertEquals("echo", services.require(Node.class, "echo").type());
        services.close();
    }

    @Test
    void duplicateNameFailsFast() throws IOException {
        jarWithNodeImpl(RogueEchoNode.class);
        var e = assertThrows(IllegalStateException.class,
                () -> Bootstrap.discover(extensionsDir));
        assertTrue(e.getMessage().contains("duplicate"));
    }

    @Test
    void missingDirectoryIsTolerated() {
        var services = Bootstrap.discover(extensionsDir.resolve("nope"));
        assertEquals("echo", services.require(Node.class, "echo").type());
        services.close();
    }

    /** Jars a node implementation class with its service declaration. */
    private void jarWithNodeImpl(Class<?> implClass) throws IOException {
        var classResource = implClass.getName().replace('.', '/') + ".class";
        try (var out = new JarOutputStream(
                Files.newOutputStream(extensionsDir.resolve("plugin.jar")))) {
            out.putNextEntry(new JarEntry(classResource));
            try (var in = implClass.getClassLoader().getResourceAsStream(classResource)) {
                in.transferTo(out);
            }
            out.closeEntry();
            out.putNextEntry(new JarEntry(
                    "META-INF/services/tech.forethought.brick.core.spi.Node"));
            out.write((implClass.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
