package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.spi.Tool;

/**
 * Reusable contract suite for {@link Tool}. Every implementation, including
 * third-party ones, runs this same suite to prove replaceability: subclass
 * it and provide the tool under test plus sample arguments.
 */
public abstract class ToolContractTest {

    protected abstract Tool subject();

    /** Sample arguments accepted by the tool under test. */
    protected Map<String, Object> sampleArguments() {
        return Map.of();
    }

    @Test
    void definitionIsPresentAndNamed() {
        assertNotNull(subject().definition());
        assertFalse(subject().definition().name().isBlank());
    }

    @Test
    void executesWithSampleArguments() {
        assertNotNull(subject().execute(sampleArguments()));
    }
}
