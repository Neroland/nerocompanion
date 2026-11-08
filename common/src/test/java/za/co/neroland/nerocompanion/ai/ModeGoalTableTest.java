package za.co.neroland.nerocompanion.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.energy.CompanionEnergy;

/** The goal-set selection matches the priority table, mode by mode. */
class ModeGoalTableTest {

    @Test
    void prioritiesMatchTheTable() {
        assertEquals(0, GoalSlot.FLOAT.priority());
        assertEquals(1, GoalSlot.SLEEP.priority());
        assertEquals(2, GoalSlot.RETURN_HOME.priority());
        assertEquals(3, GoalSlot.PERCH.priority());
        assertEquals(4, GoalSlot.WORK.priority());
        assertEquals(5, GoalSlot.FOLLOW.priority());
        assertEquals(6, GoalSlot.STAY.priority());
        assertEquals(7, GoalSlot.TRAIT_IDLE.priority());
        assertEquals(8, GoalSlot.WANDER.priority());
        assertEquals(9, GoalSlot.LOOK.priority());
    }

    @Test
    void sleepRunsOnlySleepAndReturnHome() {
        assertEquals(EnumSet.of(GoalSlot.SLEEP, GoalSlot.RETURN_HOME),
                ModeGoalTable.slotsFor(CompanionMode.SLEEP, true, true));
    }

    @Test
    void followForSmallFlyer() {
        assertEquals(EnumSet.of(GoalSlot.RETURN_HOME, GoalSlot.PERCH, GoalSlot.FOLLOW, GoalSlot.TRAIT_IDLE,
                GoalSlot.WANDER, GoalSlot.LOOK), ModeGoalTable.slotsFor(CompanionMode.FOLLOW, false, true));
    }

    @Test
    void followForLargeGroundBotHasFloatButNoPerch() {
        Set<GoalSlot> slots = ModeGoalTable.slotsFor(CompanionMode.FOLLOW, true, false);
        assertTrue(slots.contains(GoalSlot.FLOAT));
        assertFalse(slots.contains(GoalSlot.PERCH));
    }

    @Test
    void stayHoldsAndWandersNoFollow() {
        assertEquals(EnumSet.of(GoalSlot.RETURN_HOME, GoalSlot.STAY, GoalSlot.TRAIT_IDLE, GoalSlot.WANDER,
                GoalSlot.LOOK), ModeGoalTable.slotsFor(CompanionMode.STAY, false, true));
    }

    @Test
    void workModesFollowAsALeashExceptMine() {
        for (CompanionMode mode : EnumSet.of(CompanionMode.GUARD, CompanionMode.CARRY, CompanionMode.REPAIR,
                CompanionMode.SCOUT)) {
            Set<GoalSlot> slots = ModeGoalTable.slotsFor(mode, true, false);
            assertTrue(slots.containsAll(EnumSet.of(GoalSlot.WORK, GoalSlot.FOLLOW, GoalSlot.LOOK)), mode::id);
            assertFalse(slots.contains(GoalSlot.PERCH) || slots.contains(GoalSlot.WANDER), mode::id);
        }
        Set<GoalSlot> mine = ModeGoalTable.slotsFor(CompanionMode.MINE, true, false);
        assertTrue(mine.contains(GoalSlot.WORK));
        assertFalse(mine.contains(GoalSlot.FOLLOW));
    }

    @Test
    void returnHomeIsInEveryModeAndLookInEveryWakingMode() {
        for (CompanionMode mode : CompanionMode.values()) {
            Set<GoalSlot> slots = ModeGoalTable.slotsFor(mode, true, true);
            assertTrue(slots.contains(GoalSlot.RETURN_HOME), mode::id);
            assertEquals(mode != CompanionMode.SLEEP, slots.contains(GoalSlot.LOOK), mode::id);
            assertEquals(mode == CompanionMode.SLEEP, slots.contains(GoalSlot.SLEEP), mode::id);
        }
    }

    @Test
    void bondLevels() {
        assertEquals(0, za.co.neroland.nerocompanion.entity.Bond.level(0));
        assertEquals(1, za.co.neroland.nerocompanion.entity.Bond.level(100));
        assertEquals(4, za.co.neroland.nerocompanion.entity.Bond.level(100_000));
    }

    @Test
    void modeIdsRoundTripAndUnknownIsEmpty() {
        for (CompanionMode mode : CompanionMode.values()) {
            assertEquals(mode, CompanionMode.byId(mode.id()).orElseThrow());
            assertEquals(mode, CompanionMode.byOrdinal(mode.ordinal()));
        }
        assertTrue(CompanionMode.byId("dance").isEmpty());
        assertEquals(CompanionMode.SLEEP, CompanionMode.byOrdinal(99));
    }

    @Test
    void energyActivityPerMode() {
        assertEquals(CompanionEnergy.Activity.SLEEP, CompanionEnergy.activityFor(CompanionMode.SLEEP, false, false));
        assertEquals(CompanionEnergy.Activity.IDLE, CompanionEnergy.activityFor(CompanionMode.FOLLOW, false, false));
        assertEquals(CompanionEnergy.Activity.IDLE, CompanionEnergy.activityFor(CompanionMode.STAY, false, false));
        assertEquals(CompanionEnergy.Activity.IDLE, CompanionEnergy.activityFor(CompanionMode.SCOUT, true, true));
        for (CompanionMode mode : EnumSet.of(CompanionMode.GUARD, CompanionMode.MINE, CompanionMode.CARRY,
                CompanionMode.REPAIR, CompanionMode.SCOUT)) {
            assertEquals(CompanionEnergy.Activity.ACTIVE, CompanionEnergy.activityFor(mode, false, true), mode::id);
        }
    }
}
