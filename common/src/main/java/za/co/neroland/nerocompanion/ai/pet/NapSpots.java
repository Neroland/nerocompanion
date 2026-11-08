package za.co.neroland.nerocompanion.ai.pet;

import java.util.function.IntPredicate;

/**
 * Where a pet naps, as pure rules (unit-tested). In order: its home, when the home is a Charging Pad; else the last
 * Pad that charged it (a Bay-parked or Bay-hatched pet keeps the Pad it actually uses); else beside its home Bay.
 * Each candidate must be in the pet's dimension, loaded, still that block and within {@link PetNapGoal#RANGE}.
 */
public final class NapSpots {

    /** Which spot a pet naps at. */
    public enum Source {
        HOME_PAD,
        LAST_PAD,
        BESIDE_BAY,
        NONE
    }

    /** Offsets (x, y, z) tried beside a Bay, in order: north, east, south, west, at the Bay's own level. */
    public static final int[][] BESIDE = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};

    private NapSpots() {
    }

    public static Source choose(boolean homeIsPad, boolean lastPadUsable, boolean homeIsBay) {
        if (homeIsPad) {
            return Source.HOME_PAD;
        }
        if (lastPadUsable) {
            return Source.LAST_PAD;
        }
        return homeIsBay ? Source.BESIDE_BAY : Source.NONE;
    }

    /** Index into {@link #BESIDE} of the first side {@code free} accepts, or -1 when the Bay is boxed in. */
    public static int firstFree(IntPredicate free) {
        for (int i = 0; i < BESIDE.length; i++) {
            if (free.test(i)) {
                return i;
            }
        }
        return -1;
    }
}
