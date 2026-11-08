package za.co.neroland.nerocompanion.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Alert bodies are data-minimised: the companion's type and its own (filtered) name, never a position, a dimension,
 * a UUID or any other player's name.
 */
class AlertTextTest {

    private static final Pattern UUID_LIKE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-");
    private static final Pattern NUMBER = Pattern.compile("-?\\d");

    @Test
    void everyBodyNamesOnlyTypeAndName() {
        for (AlertText.Kind kind : AlertText.Kind.values()) {
            String body = AlertText.body(kind, AlertText.typeName("carry_bot"), Optional.of("Rusty"));
            assertTrue(body.contains("Carry Bot"), body);
            assertTrue(body.contains("Rusty"), body);
            assertFalse(UUID_LIKE.matcher(body).find(), "no UUID: " + body);
            assertFalse(NUMBER.matcher(body).find(), "no coordinates or numbers: " + body);
            assertFalse(body.toLowerCase().contains("overworld") || body.toLowerCase().contains("nether")
                    || body.contains("minecraft:"), "no dimension: " + body);
        }
    }

    @Test
    void theBodyCarriesNoInputButTheTypeAndTheOwnersOwnCompanionName() {
        // The only free text is the companion's own (filtered) name: swap it and nothing else changes, so no other
        // player's name, no position and no id can be in an alert or a push built from it.
        for (AlertText.Kind kind : AlertText.Kind.values()) {
            String a = AlertText.body(kind, "Carry Bot", Optional.of("Rusty")).replace("Rusty", "#");
            String b = AlertText.body(kind, "Carry Bot", Optional.of("Bolt")).replace("Bolt", "#");
            assertEquals(a, b, kind.id());
        }
    }

    @Test
    void anUnnamedCompanionIsJustItsType() {
        assertEquals("Your Mining Bot is lost. Reclaim it from a Companion Bay or with /nscomp recover.",
                AlertText.body(AlertText.Kind.LOST, AlertText.typeName("mining_bot"), Optional.empty()));
        assertEquals("Scout Drone", AlertText.typeName("scout_drone"));
        assertEquals("Companion", AlertText.typeName(""));
    }

    @Test
    void idsAreStablePerCompanionAndKindAndCarryNoUuid() {
        UUID companion = UUID.randomUUID();
        String id = AlertText.id(AlertText.Kind.LOW_BATTERY, companion);
        assertEquals(id, AlertText.id(AlertText.Kind.LOW_BATTERY, companion));
        assertFalse(id.equals(AlertText.id(AlertText.Kind.LOST, companion)));
        assertFalse(id.contains(companion.toString()));
        assertEquals("WARN", AlertText.severity(AlertText.Kind.LOST));
        assertEquals("INFO", AlertText.severity(AlertText.Kind.SALVAGE_HELD));
    }
}
