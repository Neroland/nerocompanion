package za.co.neroland.nerocompanion.charge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Charger maths: the pad's docked-first split, the 3-block reach and 4-companion limit, the Bay and the crank. */
class ChargeMathTest {

    private static ChargeMath.Want want(long missing, boolean docked, double distance) {
        return new ChargeMath.Want(missing, docked, distance);
    }

    @Test
    void dockedCompanionGetsFourHundredFirstThenNearbyGetAHundredEach() {
        long[] plan = ChargeMath.padPlan(10_000L, List.of(want(5_000L, false, 2.0D), want(5_000L, true, 0.2D),
                want(5_000L, false, 1.0D)));
        assertArrayEquals(new long[] {100L, 400L, 100L}, plan);
    }

    @Test
    void onlyOneDockedAndAtMostFourNearestWithinThreeBlocks() {
        long[] plan = ChargeMath.padPlan(100_000L, List.of(want(9_000L, true, 0.1D), want(9_000L, true, 0.3D),
                want(9_000L, false, 2.9D), want(9_000L, false, 0.5D), want(9_000L, false, 1.5D),
                want(9_000L, false, 2.5D), want(9_000L, false, 3.5D)));
        assertEquals(400L, plan[0]);
        // the second docked one counts as nearby; the four nearest within 3 blocks are served
        assertEquals(100L, plan[1]);
        assertEquals(0L, plan[2]);
        assertEquals(100L, plan[3]);
        assertEquals(100L, plan[4]);
        assertEquals(100L, plan[5]);
        assertEquals(0L, plan[6]);
    }

    @Test
    void neverGivesMoreThanAvailableOrMissing() {
        assertArrayEquals(new long[] {250L, 0L}, ChargeMath.padPlan(250L, List.of(want(5_000L, true, 0.0D),
                want(5_000L, false, 1.0D))));
        assertArrayEquals(new long[] {30L, 40L}, ChargeMath.padPlan(10_000L, List.of(want(30L, true, 0.0D),
                want(40L, false, 1.0D))));
        assertArrayEquals(new long[] {0L}, ChargeMath.padPlan(0L, List.of(want(100L, true, 0.0D))));
        long[] full = ChargeMath.padPlan(1_000L, List.of(want(0L, true, 0.0D), want(0L, false, 1.0D)));
        assertArrayEquals(new long[] {0L, 0L}, full);
    }

    @Test
    void bayChargesEachParkedCompanionUpToTwoHundredInSlotOrder() {
        assertArrayEquals(new long[] {200L, 50L, 0L, 200L},
                ChargeMath.bayPlan(10_000L, new long[] {1_000L, 50L, 0L, 900L}));
        assertArrayEquals(new long[] {200L, 100L, 0L, 0L},
                ChargeMath.bayPlan(300L, new long[] {1_000L, 1_000L, 1_000L, 1_000L}));
    }

    @Test
    void crankGivesSixtyPerCrankOncePerFiveTicks() {
        assertEquals(60L, ChargeMath.crank(1_000L));
        assertEquals(15L, ChargeMath.crank(15L));
        assertEquals(0L, ChargeMath.crank(0L));
        assertTrue(ChargeMath.crankReady(100L, 105L));
        assertFalse(ChargeMath.crankReady(100L, 104L));
        // 60 FE per 5 ticks = 240 FE/s: a 6 000 FE Scrapling fills in 25 s
        assertEquals(25L, 6_000L / (ChargeMath.CRANK_FE * (20 / ChargeMath.CRANK_COOLDOWN_TICKS)));
    }

    @Test
    void coreBoundaryConvertsNeAndFeWithoutGivingEnergyAway() {
        assertEquals(100_000L, ChargeMath.feAvailable(100_000L, 1.0D));
        assertEquals(50_000L, ChargeMath.bufferNe(100_000L, 2.0D));
        assertEquals(200_000L, ChargeMath.feAvailable(100_000L, 2.0D));
        assertEquals(2L, ChargeMath.neCost(3L, 2.0D));
        assertEquals(400L, ChargeMath.neCost(400L, 1.0D));
        assertEquals(0L, ChargeMath.feAvailable(10L, 0.0D));
    }

    @Test
    void timeToFull() {
        assertEquals(60L, ChargeMath.ticksToFull(24_000L, 400L));
        assertEquals(1L, ChargeMath.ticksToFull(1L, 400L));
        assertEquals(0L, ChargeMath.ticksToFull(100L, 0L));
    }
}
