package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;
import java.util.Set;

import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * The goal-set selection, as a pure function: which {@link GoalSlot}s a companion runs in a
 * given (effective) mode. Shared goals stay in every mode except SLEEP.
 *
 * <pre>
 * Pri  Slot         Modes
 *  0   FLOAT        all but SLEEP (ground chassis only)
 *  1   SLEEP        SLEEP
 *  2   RETURN_HOME  all
 *  3   PERCH        FOLLOW (small chassis only)
 *  4   WORK         GUARD, MINE, CARRY, REPAIR, SCOUT
 *  5   FOLLOW       FOLLOW, CARRY, REPAIR, SCOUT, GUARD (leash)
 *  6   STAY         STAY
 *  7   TRAIT_IDLE   FOLLOW, STAY
 *  8   WANDER       FOLLOW, STAY
 *  9   LOOK         all but SLEEP
 * </pre>
 */
public final class ModeGoalTable {

    private ModeGoalTable() {
    }

    public static Set<GoalSlot> slotsFor(CompanionMode mode, boolean ground, boolean small) {
        EnumSet<GoalSlot> slots = EnumSet.of(GoalSlot.RETURN_HOME);
        if (mode == CompanionMode.SLEEP) {
            slots.add(GoalSlot.SLEEP);
            return slots;
        }
        if (ground) {
            slots.add(GoalSlot.FLOAT);
        }
        slots.add(GoalSlot.LOOK);
        switch (mode) {
            case FOLLOW -> {
                slots.add(GoalSlot.FOLLOW);
                slots.add(GoalSlot.TRAIT_IDLE);
                slots.add(GoalSlot.WANDER);
                if (small) {
                    slots.add(GoalSlot.PERCH);
                }
            }
            case STAY -> {
                slots.add(GoalSlot.STAY);
                slots.add(GoalSlot.TRAIT_IDLE);
                slots.add(GoalSlot.WANDER);
            }
            case GUARD, CARRY, REPAIR, SCOUT -> {
                slots.add(GoalSlot.WORK);
                slots.add(GoalSlot.FOLLOW);
            }
            case MINE -> slots.add(GoalSlot.WORK);
            default -> {
            }
        }
        return slots;
    }
}
