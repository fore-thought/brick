package tech.forethought.brick.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tech.forethought.brick.core.TextTransformer;

/**
 * Reusable contract suite for {@link TextTransformer}. Every implementation,
 * including third-party ones, runs this same suite to prove replaceability:
 * subclass it and provide the instance under test.
 */
abstract class TextTransformerContractTest {

    protected abstract TextTransformer subject();

    @Test
    void nameIsNotBlank() {
        assertFalse(subject().name().isBlank());
    }

    @Test
    void appliesWithoutReturningNull() {
        assertNotNull(subject().apply("Brick"));
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertEquals("", subject().apply(""));
    }

    @Test
    void nullInputThrows() {
        assertThrows(NullPointerException.class, () -> subject().apply(null));
    }

    @Test
    void isDeterministic() {
        var subject = subject();
        assertEquals(subject.apply("abc"), subject.apply("abc"));
    }
}
