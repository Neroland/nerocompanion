package za.co.neroland.nerocompanion.telemetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Nothing personal reaches Sentry: every message, exception value and breadcrumb goes through
 * {@link NeroCompanionTelemetry#scrub}, which masks OS-account paths, UUIDs, vanilla's quoted entity names, owner and
 * name fields, block positions, coordinates and level / dimension keys.
 */
class TelemetryScrubTest {

    @Test
    void uuidsNamesPositionsAndDimensionsAreMasked() {
        UUID owner = UUID.randomUUID();
        String raw = "Ticking companion ServerPlayer['Steve'/42, l='ServerLevel[My World]', x=12.50, y=64.00, "
                + "z=-3.25] owner=" + owner + " CompanionRecord[name=Optional[Rusty], ownerId=" + owner
                + ", home=Optional[HomePos[dimension=nerospace:luna, pos=BlockPos{x=101, y=70, z=-202}]]] at (1.5, 2.5, "
                + "-3.5) in ResourceKey[minecraft:dimension / minecraft:the_nether] from C:\\Users\\dario\\mods";
        String out = NeroCompanionTelemetry.scrub(raw);

        assertFalse(out.contains(owner.toString()), out);
        assertFalse(out.contains("Steve"), out);
        assertFalse(out.contains("Rusty"), out);
        assertFalse(out.contains("dario"), out);
        assertFalse(out.contains("My World"), out);
        assertFalse(out.contains("luna") || out.contains("the_nether"), out);
        for (String number : new String[] {"12.50", "64.00", "3.25", "101", "202", "1.5", "2.5", "3.5"}) {
            assertFalse(out.contains(number), number + " in " + out);
        }
        assertTrue(out.contains("Ticking companion"), "the useful part stays: " + out);
    }

    @Test
    void stackTraceTextWithoutPersonalDataIsLeftReadable() {
        String raw = "java.lang.IllegalStateException: chip slot 3 out of range at "
                + "za.co.neroland.nerocompanion.chip.ChipRules.check(ChipRules.java:120)";
        String out = NeroCompanionTelemetry.scrub(raw);
        assertTrue(out.contains("ChipRules.java:120"), out);
        assertTrue(out.contains("chip slot 3 out of range"), out);
    }
}
