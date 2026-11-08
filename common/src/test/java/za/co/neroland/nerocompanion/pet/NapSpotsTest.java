package za.co.neroland.nerocompanion.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.pet.NapSpots;
import za.co.neroland.nerocompanion.ai.pet.NapSpots.Source;
import za.co.neroland.nerocompanion.incubation.EggPrompts;

/** Where a pet naps (home Pad, last Pad, beside its Bay) and the egg take-back confirm. */
class NapSpotsTest {

    @Test
    void homePadWinsThenLastPadThenBay() {
        assertEquals(Source.HOME_PAD, NapSpots.choose(true, true, false));
        assertEquals(Source.HOME_PAD, NapSpots.choose(true, false, false));
        // Parked once or hatched in a Bay: home is the Bay, but the Pad it charged on is remembered.
        assertEquals(Source.LAST_PAD, NapSpots.choose(false, true, true));
        // Never charged on a Pad: it still has a bedtime, beside its Bay.
        assertEquals(Source.BESIDE_BAY, NapSpots.choose(false, false, true));
        assertEquals(Source.NONE, NapSpots.choose(false, false, false));
    }

    @Test
    void firstFreeSideBesideTheBay() {
        assertEquals(0, NapSpots.firstFree(i -> true));
        assertEquals(2, NapSpots.firstFree(i -> i == 2 || i == 3));
        assertEquals(-1, NapSpots.firstFree(i -> false), "a boxed-in Bay has no spot");
        assertEquals(4, NapSpots.BESIDE.length);
        for (int[] offset : NapSpots.BESIDE) {
            assertEquals(1, Math.abs(offset[0]) + Math.abs(offset[2]), "one block beside, horizontally");
            assertEquals(0, offset[1]);
        }
    }

    @Test
    void secondEmptyHandUseWithinThreeSecondsTakesTheEggBack() {
        String pad = "pad";
        assertTrue(EggPrompts.confirms(pad, 100L, "pad", 100L + EggPrompts.CONFIRM_TICKS));
        assertFalse(EggPrompts.confirms(pad, 100L, "pad", 101L + EggPrompts.CONFIRM_TICKS), "too late: shows again");
        assertFalse(EggPrompts.confirms(pad, 100L, "other", 110L), "another incubator");
        assertFalse(EggPrompts.confirms(null, 100L, "pad", 110L), "first use only shows progress");
        assertFalse(EggPrompts.confirms(pad, 100L, "pad", 90L), "clock went back (new world)");
    }
}
