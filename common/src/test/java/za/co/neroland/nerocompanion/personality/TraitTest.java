package za.co.neroland.nerocompanion.personality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** Trait selection from the personality seed. */
class TraitTest {

    @Test
    void sameSeedAlwaysSameTrait() {
        Random random = new Random(42L);
        for (int i = 0; i < 1000; i++) {
            long seed = random.nextLong();
            assertEquals(Trait.fromSeed(seed), Trait.fromSeed(seed));
        }
    }

    @Test
    void randomSeedsCoverAllSixEvenly() {
        Random random = new Random(7L);
        Map<Trait, Integer> counts = new EnumMap<>(Trait.class);
        int n = 60_000;
        for (int i = 0; i < n; i++) {
            counts.merge(Trait.fromSeed(random.nextLong()), 1, Integer::sum);
        }
        assertEquals(6, counts.size());
        for (int count : counts.values()) {
            assertTrue(Math.abs(count - n / 6) < n / 60, "uneven: " + counts);
        }
    }

    @Test
    void sequentialSeedsCycleThroughAllSix() {
        for (long seed = -12; seed < 12; seed++) {
            assertEquals(Math.floorMod(seed, 6), Trait.fromSeed(seed).ordinal());
        }
    }

    @Test
    void indexMatchesTheSynchedByteAndFallsBack() {
        assertEquals(Trait.COUNT, Trait.values().length);
        // Order is part of the save/sync format: curious, loyal, grumpy, sleepy, showoff, shy.
        assertEquals("curious", Trait.byIndex(0).id());
        assertEquals("shy", Trait.byIndex(5).id());
        assertEquals(Trait.CURIOUS, Trait.byIndex(6));
        assertEquals(Trait.CURIOUS, Trait.byIndex(-1));
        for (Trait trait : Trait.values()) {
            assertEquals(trait, Trait.byIndex(trait.ordinal()));
        }
    }
}
