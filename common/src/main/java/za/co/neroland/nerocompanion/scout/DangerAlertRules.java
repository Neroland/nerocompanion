package za.co.neroland.nerocompanion.scout;

/**
 * The Danger Alert (pure): a hostile within {@value #RANGE} blocks of the owner and <b>behind</b> them (in the half
 * space opposite their horizontal look direction) makes the drone chirp and flash, at most once per
 * {@value #COOLDOWN_TICKS} ticks and once per mob until that mob leaves the range ({@link Tracker}). No alert while
 * the owner is fighting (hit a mob in the last {@value #COMBAT_TICKS} ticks) or in creative; neutral mobs count only
 * while they target the owner. Checked on the {@value #CHECK_TICKS}-tick scan cadence, never per tick.
 */
public final class DangerAlertRules {

    public static final double RANGE = 8.0D;
    public static final int COOLDOWN_TICKS = 60;
    public static final int CHECK_TICKS = 10;
    public static final int COMBAT_TICKS = 60;

    private DangerAlertRules() {
    }

    /**
     * Whether a hostile at offset {@code (dx, dy, dz)} from the owner is a danger: within range, and behind the
     * horizontal look vector {@code (lookX, lookZ)}. A hostile straight above or below (no horizontal offset) or an
     * owner looking straight up/down (no horizontal look) counts as behind - they cannot see it either.
     */
    public static boolean isDanger(double lookX, double lookZ, double dx, double dy, double dz) {
        if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
            return false;
        }
        double look = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double off = Math.sqrt(dx * dx + dz * dz);
        if (look < 1.0E-6D || off < 1.0E-6D) {
            return true;
        }
        return (lookX * dx + lookZ * dz) / (look * off) < 0.0D;
    }

    /** A per-drone cooldown. */
    public static final class Cooldown {
        private long nextAllowed = Long.MIN_VALUE;

        /** True (and the cooldown restarts) if an alert may fire at game tick {@code now}. */
        public boolean tryFire(long now) {
            if (now < this.nextAllowed) {
                return false;
            }
            this.nextAllowed = now + COOLDOWN_TICKS;
            return true;
        }
    }

    /** Whether the owner hit a mob recently ({@code lastHurtMobTimestamp} and {@code ownerTick} are tick counts). */
    public static boolean inCombat(int lastHurtMobTimestamp, int ownerTick) {
        return lastHurtMobTimestamp > 0 && ownerTick - lastHurtMobTimestamp < COMBAT_TICKS;
    }

    /** Remembers which mobs were already alerted for, until they leave the range. */
    public static final class Tracker {
        private final java.util.Set<Integer> alerted = new java.util.HashSet<>();

        /**
         * One check. {@code inRange}: ids of all qualifying hostiles within range; {@code danger}: those behind the
         * owner. True if an alert should fire now (a not-yet-alerted danger and the cooldown allows it).
         */
        public boolean check(java.util.Set<Integer> inRange, java.util.Set<Integer> danger, Cooldown cooldown,
                long now) {
            this.alerted.retainAll(inRange);
            boolean fresh = false;
            for (Integer id : danger) {
                if (!this.alerted.contains(id)) {
                    fresh = true;
                    break;
                }
            }
            if (!fresh || !cooldown.tryFire(now)) {
                return false;
            }
            this.alerted.addAll(danger);
            return true;
        }
    }
}
