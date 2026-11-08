package za.co.neroland.nerocompanion.ai.pet;

/** Which play idle a pet picks (pure; unit-tested). Play is cosmetic: it moves, spins or hops, and touches nothing. */
public final class PlayRules {

    /** The play idles. */
    public enum Kind {
        /** A few quick dashes to nearby spots (never while told to STAY). */
        ZOOMIES,
        /** Chases its own tail in place. */
        SPIN,
        /** A small hop toward the owner. */
        POUNCE
    }

    /** Checked on this cadence (ticks). */
    public static final int CHECK_INTERVAL = 20;
    /** One chance in this many per check (about one play every 30 seconds near the owner). */
    public static final int CHANCE_IN = 30;
    /** Rest after a play (ticks). */
    public static final int COOLDOWN = 600;
    /** Longest play (ticks). */
    public static final int DURATION = 60;
    /** Plays only within this distance of the owner. */
    public static final double NEAR = 8.0D;

    private PlayRules() {
    }

    /** The play for a roll in {@code 0..99}; a staying pet never runs off. */
    public static Kind pick(int roll, boolean staying) {
        int r = Math.floorMod(roll, 100);
        if (staying) {
            return r < 50 ? Kind.SPIN : Kind.POUNCE;
        }
        return r < 40 ? Kind.ZOOMIES : r < 70 ? Kind.SPIN : Kind.POUNCE;
    }
}
