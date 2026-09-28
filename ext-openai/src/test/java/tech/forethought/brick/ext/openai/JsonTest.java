package tech.forethought.brick.ext.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void roundTripsNestedStructures() {
        var value = new LinkedHashMap<String, Object>();
        value.put("text", "hi");
        value.put("nested", Map.of("list", List.of(1L, 2.5, true), "empty", List.of()));
        value.put("nothing", null);
        assertEquals(value, Json.read(Json.write(value)));
    }

    @Test
    void escapesStrings() {
        assertEquals("say \"hi\"\n", Json.read(Json.write("say \"hi\"\n")));
        assertEquals("back\\slash", Json.read(Json.write("back\\slash")));
    }

    @Test
    void readsUnicodeEscapes() {
        assertEquals("砖", Json.read("\"\\u7816\""));
    }

    @Test
    void parsesNumbers() {
        assertEquals(42L, Json.read("42"));
        assertEquals(4.2, Json.read("4.2"));
        assertEquals(-3L, Json.read("-3"));
        assertEquals(1000.0, Json.read("1e3"));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.read("{"));
        assertThrows(IllegalArgumentException.class, () -> Json.read("{} trailing"));
        assertThrows(IllegalArgumentException.class, () -> Json.read("[1,]"));
    }
}
