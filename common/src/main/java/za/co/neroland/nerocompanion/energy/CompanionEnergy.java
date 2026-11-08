package za.co.neroland.nerocompanion.energy;

import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * A companion's FE buffer. Plain FE, {@code long}, no Core NE conversion here: chargers convert at
 * their own Core boundary (Stage 2). Drains are per second and applied once every 20 ticks by the scheduler, so
 * there is never a fractional per-tick FE amount.
 *
 * <p>Pure Java (no game classes) so the drain rules are unit-tested headless.
 */
public final class CompanionEnergy {

    /** Percent at or below which a companion heads home (or sleeps in place). Default; config overrides. */
    public static final int DEFAULT_LOW_PERCENT = 10;

    /** The low-battery alert re-arms once charge climbs back above this percent. */
    public static final int REARM_PERCENT = 50;

    /** What a companion is doing, for drain purposes. */
    public enum Activity {
        /** SLEEP: no drain. */
        SLEEP,
        /** FOLLOW, STAY, perched. */
        IDLE,
        /** GUARD, MINE, CARRY, REPAIR, SCOUT. */
        ACTIVE
    }

    /**
     * Energy activity for an effective mode ({@code 003}): SLEEP none; FOLLOW, STAY and perched idle; SCOUT active;
     * GUARD, REPAIR, MINE and CARRY active only while {@code working} (engaged with a target, mending an item, digging a
     * job, hauling a load) and idle otherwise, so a bot waiting for work or with its job paused pays the idle rate.
     */
    public static Activity activityFor(CompanionMode effective, boolean perched, boolean working) {
        if (effective == CompanionMode.SLEEP) {
            return Activity.SLEEP;
        }
        if (perched || !effective.isActiveDrain()) {
            return Activity.IDLE;
        }
        if (effective != CompanionMode.SCOUT && !working) {
            return Activity.IDLE;
        }
        return Activity.ACTIVE;
    }

    private long stored;
    private long capacity;
    private boolean lowAlertArmed = true;

    public CompanionEnergy(long capacity, long stored) {
        this.capacity = Math.max(1L, capacity);
        this.stored = clamp(stored, this.capacity);
    }

    private static long clamp(long value, long capacity) {
        return Math.max(0L, Math.min(capacity, value));
    }

    public long stored() {
        return this.stored;
    }

    public long capacity() {
        return this.capacity;
    }

    /** Changes the capacity (type reload, capacity chip removed); stored energy is clamped to it. */
    public void setCapacity(long capacity) {
        this.capacity = Math.max(1L, capacity);
        this.stored = clamp(this.stored, this.capacity);
    }

    public void setStored(long stored) {
        this.stored = clamp(stored, this.capacity);
        if (percent() > REARM_PERCENT) {
            this.lowAlertArmed = true;
        }
    }

    /** Charge as a whole percent, 0-100. Rounded down, except that any charge at all reads as at least 1. */
    public int percent() {
        if (this.stored <= 0L) {
            return 0;
        }
        return (int) Math.max(1L, Math.min(100L, this.stored * 100L / this.capacity));
    }

    public boolean isEmpty() {
        return this.stored <= 0L;
    }

    /** At or below {@code lowPercent} of capacity (and not empty-by-construction for a full buffer). */
    public boolean isLow(int lowPercent) {
        return this.stored * 100L <= lowPercent * this.capacity;
    }

    /**
     * Applies one second of drain for {@code activity}.
     *
     * @return the FE actually drained
     */
    public long drainSecond(Activity activity, long drainIdlePerSecond, long drainActivePerSecond) {
        long want = switch (activity) {
            case SLEEP -> 0L;
            case IDLE -> Math.max(0L, drainIdlePerSecond);
            case ACTIVE -> Math.max(0L, drainActivePerSecond);
        };
        long taken = Math.min(want, this.stored);
        this.stored -= taken;
        return taken;
    }

    /** Pays a per-action cost (mining a block, a repair point, a hit). False, and nothing taken, if short. */
    public boolean tryPay(long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (this.stored < amount) {
            return false;
        }
        this.stored -= amount;
        return true;
    }

    /** Adds FE (chargers, Stage 2). Returns what was accepted. */
    public long insert(long amount) {
        long accepted = Math.max(0L, Math.min(amount, this.capacity - this.stored));
        this.stored += accepted;
        if (percent() > REARM_PERCENT) {
            this.lowAlertArmed = true;
        }
        return accepted;
    }

    /** After a load: arm the alert only if not already low (a companion saved low has already alerted). */
    public void restoreAlertState(int lowPercent) {
        this.lowAlertArmed = !isLow(lowPercent);
    }

    /**
     * True exactly once per discharge when the buffer is low: the caller raises the low-battery alert. Re-armed
     * when the charge climbs back above {@link #REARM_PERCENT}.
     */
    public boolean takeLowAlert(int lowPercent) {
        if (this.lowAlertArmed && isLow(lowPercent)) {
            this.lowAlertArmed = false;
            return true;
        }
        return false;
    }
}
