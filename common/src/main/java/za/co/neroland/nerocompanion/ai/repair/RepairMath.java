package za.co.neroland.nerocompanion.ai.repair;

import java.util.List;
import java.util.OptionalInt;

/**
 * The Repair Bot's numbers and its choice of item, pure (no game classes) so they are unit-tested headless.
 *
 * <p>Energy balance ({@code 003}): a durability point costs {@link #FE_PER_POINT} FE. The bot restores a few points
 * per tick and never spends below the {@link #RESERVE_PERCENT} % reserve - the same line at which it turns for home,
 * so repairing can never strand it.
 */
public final class RepairMath {

    /** FE per durability point restored ({@code 003}). */
    public static final long FE_PER_POINT = 2L;

    /** Points restored per tick at base speed (40 points a second, 80 FE/s while repairing). */
    public static final int POINTS_PER_TICK = 2;

    /** Never spend below this percent of capacity (the low-battery line, {@code 003}). */
    public static final int RESERVE_PERCENT = 10;

    /** Ticks between scans of the owner's gear. */
    public static final int SCAN_INTERVAL = 40;

    /** Blocks from the owner within which the bot repairs, before range chips. */
    public static final int BASE_RANGE = 6;

    private RepairMath() {
    }

    /** One inventory slot as the chooser sees it. */
    public record Slot(int index, boolean damageable, int damage, int maxDamage) {

        boolean repairable() {
            return this.damageable && this.maxDamage > 0 && this.damage > 0;
        }
    }

    /** FE held back: {@code reservePercent} of capacity, rounded up. */
    public static long reserve(long capacity, int reservePercent) {
        long cap = Math.max(0L, capacity);
        long pct = Math.max(0, Math.min(100, reservePercent));
        return (cap * pct + 99L) / 100L;
    }

    /** FE for {@code points} durability points. */
    public static long cost(int points) {
        return Math.max(0, points) * FE_PER_POINT;
    }

    /**
     * How many of {@code wanted} points the bot can pay for this tick without dipping below the reserve.
     * Zero when it is at or below the reserve.
     */
    public static int affordablePoints(long stored, long capacity, int wanted) {
        if (wanted <= 0) {
            return 0;
        }
        long spendable = stored - reserve(capacity, RESERVE_PERCENT);
        if (spendable < FE_PER_POINT) {
            return 0;
        }
        return (int) Math.min(wanted, spendable / FE_PER_POINT);
    }

    /** Points per tick after the speed chip's work-speed multiplier (at least 1). */
    public static int pointsPerTick(double speedMultiplier) {
        return Math.max(1, (int) Math.round(POINTS_PER_TICK * Math.max(0.0D, speedMultiplier)));
    }

    /** Repair range in blocks after range chips. */
    public static int range(int rangeBonus) {
        return BASE_RANGE + Math.max(0, rangeBonus);
    }

    /**
     * The most-damaged repairable slot: highest share of durability lost; ties go to more points lost, then to the
     * earlier slot. Items that cannot take damage (unbreakable, not a tool) are never chosen.
     */
    public static OptionalInt mostDamaged(List<Slot> slots) {
        Slot best = null;
        for (Slot slot : slots) {
            if (!slot.repairable()) {
                continue;
            }
            if (best == null || worse(slot, best)) {
                best = slot;
            }
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(best.index());
    }

    /** {@code a} is more damaged than {@code b}: compares damage/max by cross-multiplication (no rounding). */
    private static boolean worse(Slot a, Slot b) {
        long left = (long) a.damage() * b.maxDamage();
        long right = (long) b.damage() * a.maxDamage();
        if (left != right) {
            return left > right;
        }
        return a.damage() > b.damage();
    }
}
