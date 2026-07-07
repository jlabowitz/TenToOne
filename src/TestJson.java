import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * design/persistent-game-state.md §4: hand-rolled JSON writer/parser, scoped
 * to exactly what GameStateStore needs (ints, strings, booleans, nested
 * objects/lists) -- not a general-purpose JSON library. Json.write/parse
 * round-trip through a generic Map/List/String/Number/Boolean/null tree, the
 * same shape GameStateStore maps GameStateSnapshot to/from.
 */
public class TestJson {
    @Test
    public void writesAndParsesAFlatObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("score", 42);
        map.put("hasBet", true);

        String text = Json.write(map);
        Object parsed = Json.parse(text);

        assertTrue(parsed instanceof Map);
        Map<?, ?> result = (Map<?, ?>) parsed;
        assertEquals("Alice", result.get("name"));
        assertEquals(42L, ((Number) result.get("score")).longValue());
        assertEquals(true, result.get("hasBet"));
    }

    @Test
    public void writesAndParsesNestedObjectsAndArrays() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("suit", "HEARTS");
        card.put("value", "ACE");

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("hand", List.of(card));

        String text = Json.write(player);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(text);
        List<?> hand = (List<?>) parsed.get("hand");
        Map<?, ?> parsedCard = (Map<?, ?>) hand.get(0);

        assertEquals("HEARTS", parsedCard.get("suit"));
        assertEquals("ACE", parsedCard.get("value"));
    }

    @Test
    public void writesAndParsesNullValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("round", null);

        String text = Json.write(map);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(text);

        assertTrue(parsed.containsKey("round"));
        assertNull(parsed.get("round"));
    }

    @Test
    public void escapesAndUnescapesSpecialCharactersInStrings() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Quote\"Backslash\\Newline\nTab\tEnd");

        String text = Json.write(map);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(text);

        assertEquals("Quote\"Backslash\\Newline\nTab\tEnd", parsed.get("name"));
    }

    @Test
    public void writesAndParsesEmptyArraysAndObjects() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("emptyList", List.of());
        map.put("emptyObject", new LinkedHashMap<>());

        String text = Json.write(map);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(text);

        assertTrue(((List<?>) parsed.get("emptyList")).isEmpty());
        assertTrue(((Map<?, ?>) parsed.get("emptyObject")).isEmpty());
    }

    @Test
    public void parsesNegativeAndDecimalNumbers() {
        String text = "{\"a\": -5, \"b\": 3.5}";
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(text);

        assertEquals(-5L, ((Number) parsed.get("a")).longValue());
        assertEquals(3.5, ((Number) parsed.get("b")).doubleValue(), 0.0001);
    }
}
