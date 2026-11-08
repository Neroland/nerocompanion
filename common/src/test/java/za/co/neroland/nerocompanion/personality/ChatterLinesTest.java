package za.co.neroland.nerocompanion.personality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** The chatter line pools. */
class ChatterLinesTest {

    @Test
    void everyTraitHasAtLeastTwelveLines() {
        for (Trait trait : Trait.values()) {
            assertTrue(ChatterLines.pool(trait).size() >= 12, trait.id());
        }
    }

    @Test
    void keysAreUniqueAndWellFormed() {
        List<String> keys = ChatterLines.allKeys();
        assertEquals(keys.size(), new HashSet<>(keys).size());
        for (String key : keys) {
            assertTrue(key.matches("chatter\\.nerocompanion\\.[a-z]+\\.[0-9a-z]+"), key);
            assertEquals(key, ChatterLines.byKey(key).orElseThrow().key());
        }
        assertFalse(ChatterLines.byKey("chatter.nerocompanion.curious.999").isPresent());
    }

    @Test
    void everyTraitCanAnswerEveryEvent() {
        for (Trait trait : Trait.values()) {
            Set<ChatterEvent> covered = new HashSet<>();
            for (ChatterLines.Line line : ChatterLines.pool(trait)) {
                assertEquals(trait, line.trait());
                covered.add(line.event());
            }
            for (ChatterEvent event : ChatterEvent.values()) {
                ChatterLines.shared(event).ifPresent(line -> covered.add(event));
            }
            assertEquals(Set.of(ChatterEvent.values()), covered, trait.id());
        }
    }

    @Test
    void pickIsDeterministicAndStaysInTheEventPool() {
        for (Trait trait : Trait.values()) {
            for (ChatterEvent event : ChatterEvent.values()) {
                for (int roll = -5; roll < 50; roll++) {
                    ChatterLines.Line line = ChatterLines.pick(trait, event, roll);
                    assertEquals(line, ChatterLines.pick(trait, event, roll));
                    assertEquals(event, line.event());
                    if (ChatterLines.shared(event).isEmpty()) {
                        assertEquals(trait, line.trait());
                    }
                }
            }
        }
    }

    @Test
    void dangerIsTheScoutsSharedLine() {
        for (Trait trait : Trait.values()) {
            ChatterLines.Line line = ChatterLines.pick(trait, ChatterEvent.DANGER, 3);
            assertEquals(ChatterLines.DANGER_KEY, line.key());
            assertEquals(Emote.DANGER, line.first());
        }
    }

    @Test
    void everyKeyHasEnglishText() throws Exception {
        try (InputStream in = ChatterLinesTest.class.getResourceAsStream("/assets/nerocompanion/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the test classpath");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (String key : ChatterLines.allKeys()) {
                assertTrue(lang.has(key), "missing lang key " + key);
                assertFalse(lang.get(key).getAsString().isBlank(), key);
            }
        }
    }

    @Test
    void iconsAreInTheSheet() {
        for (String key : ChatterLines.allKeys()) {
            ChatterLines.Line line = ChatterLines.byKey(key).orElseThrow();
            assertTrue(line.first().row() < Emote.ROWS);
            assertEquals(Emote.COLUMNS * Emote.ROWS, Emote.values().length);
        }
    }
}
