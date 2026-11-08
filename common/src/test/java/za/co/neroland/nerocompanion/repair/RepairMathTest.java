package za.co.neroland.nerocompanion.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.repair.RepairMath;
import za.co.neroland.nerocompanion.ai.repair.RepairMath.Slot;
import za.co.neroland.nerocompanion.ai.repair.RepairWork;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;

/** Repair cost and reserve maths, and the choice of the most-damaged item. */
class RepairMathTest {

    private static final long CAPACITY = 36_000L;

    @Test
    void costIsTwoFePerPoint() {
        assertEquals(2L, RepairMath.FE_PER_POINT);
        assertEquals(0L, RepairMath.cost(0));
        assertEquals(2L, RepairMath.cost(1));
        assertEquals(500L, RepairMath.cost(250));
        assertEquals(0L, RepairMath.cost(-3));
    }

    @Test
    void reserveIsTenPercentRoundedUp() {
        assertEquals(3_600L, RepairMath.reserve(CAPACITY, RepairMath.RESERVE_PERCENT));
        assertEquals(1L, RepairMath.reserve(1L, 10));
        assertEquals(0L, RepairMath.reserve(0L, 10));
        assertEquals(11L, RepairMath.reserve(101L, 10));
    }

    @Test
    void fullBatteryPaysForEverythingWanted() {
        assertEquals(2, RepairMath.affordablePoints(CAPACITY, CAPACITY, 2));
    }

    @Test
    void stopsExactlyAtTheReserve() {
        long reserve = RepairMath.reserve(CAPACITY, RepairMath.RESERVE_PERCENT);
        assertEquals(0, RepairMath.affordablePoints(reserve, CAPACITY, 2));
        assertEquals(0, RepairMath.affordablePoints(reserve + 1, CAPACITY, 2));
        assertEquals(1, RepairMath.affordablePoints(reserve + 2, CAPACITY, 2));
        assertEquals(1, RepairMath.affordablePoints(reserve + 3, CAPACITY, 2));
        assertEquals(2, RepairMath.affordablePoints(reserve + 4, CAPACITY, 2));
        assertEquals(0, RepairMath.affordablePoints(0L, CAPACITY, 2));
        assertEquals(0, RepairMath.affordablePoints(CAPACITY, CAPACITY, 0));
    }

    @Test
    void neverSpendsBelowTheReserveOverAWholeDrain() {
        long stored = CAPACITY;
        long reserve = RepairMath.reserve(CAPACITY, RepairMath.RESERVE_PERCENT);
        int restored = 0;
        for (int tick = 0; tick < 100_000; tick++) {
            int points = RepairMath.affordablePoints(stored, CAPACITY, RepairMath.pointsPerTick(1.0D));
            if (points == 0) {
                break;
            }
            stored -= RepairMath.cost(points);
            restored += points;
            assertTrue(stored >= reserve);
        }
        assertEquals(reserve, stored);
        assertEquals((CAPACITY - reserve) / RepairMath.FE_PER_POINT, restored);
    }

    @Test
    void speedChipRaisesPointsPerTick() {
        assertEquals(2, RepairMath.pointsPerTick(1.0D));
        assertEquals(2, RepairMath.pointsPerTick(1.1D));
        assertEquals(3, RepairMath.pointsPerTick(1.3D));
        assertEquals(1, RepairMath.pointsPerTick(0.0D));
    }

    @Test
    void rangeIsSixPlusChips() {
        assertEquals(6, RepairMath.range(0));
        assertEquals(12, RepairMath.range(6));
    }

    @Test
    void picksTheHighestShareOfDurabilityLost() {
        List<Slot> slots = List.of(
                new Slot(0, true, 100, 400),   // 25 %
                new Slot(5, true, 30, 59),     // ~51 %
                new Slot(6, true, 1000, 2031)); // ~49 %
        assertEquals(OptionalInt.of(5), RepairMath.mostDamaged(slots));
    }

    @Test
    void tiesGoToMorePointsThenEarlierSlot() {
        assertEquals(OptionalInt.of(2), RepairMath.mostDamaged(List.of(
                new Slot(1, true, 10, 100), new Slot(2, true, 100, 1000))));
        assertEquals(OptionalInt.of(1), RepairMath.mostDamaged(List.of(
                new Slot(1, true, 10, 100), new Slot(3, true, 10, 100))));
    }

    @Test
    void neverPicksUndamagedOrUndamageableItems() {
        assertEquals(OptionalInt.empty(), RepairMath.mostDamaged(List.of(
                new Slot(0, false, 50, 100),  // unbreakable / not a tool
                new Slot(1, true, 0, 250),     // pristine
                new Slot(2, true, 5, 0))));    // no max damage
        assertEquals(OptionalInt.empty(), RepairMath.mostDamaged(List.of()));
    }

    @Test
    void registersItsWorkGoalOnce() {
        RepairWork.register();
        RepairWork.register();
        assertTrue(WorkGoals.known().contains(RepairWork.REPAIR_GEAR));
    }
}
