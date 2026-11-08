package za.co.neroland.nerocompanion.charge;

import java.util.ArrayList;
import java.util.List;

/**
 * Charger numbers (pure). Every number players see is FE; the Pad and the Bay hold Core energy (NE) and convert
 * only at that boundary, at the server's configured ratio. Defaults: Pad 400 FE/t to the companion docked on it
 * plus 100 FE/t to each of up to 4 within 3 blocks; Bay 200 FE/t per parked companion; Hand Crank 60 FE per crank,
 * one crank per 5 ticks per player.
 */
public final class ChargeMath {

    public static final long PAD_BUFFER_FE = 100_000L;
    public static final long PAD_DOCKED_FE_PER_TICK = 400L;
    public static final long PAD_NEARBY_FE_PER_TICK = 100L;
    public static final int PAD_NEARBY_MAX = 4;
    public static final double PAD_NEARBY_RANGE = 3.0D;

    public static final long BAY_BUFFER_FE = 200_000L;
    public static final long BAY_FE_PER_TICK = 200L;
    public static final int BAY_SLOTS = 4;

    public static final long CRANK_FE = 60L;
    public static final int CRANK_COOLDOWN_TICKS = 5;
    public static final float CRANK_EXHAUSTION = 0.05F;

    private ChargeMath() {
    }

    /** One companion a pad may charge this tick: how much it can still take and whether it is docked on the pad. */
    public record Want(long missingFe, boolean docked, double distance) {
    }

    /**
     * Splits {@code availableFe} across the companions near a pad. The docked one (at most one, the first docked
     * in the list) gets up to 400 FE/t first; then up to 4 others within 3 blocks get up to 100 FE/t each, nearest
     * first. Nobody gets more than it is missing; the total never exceeds {@code availableFe}.
     *
     * @return the FE for each entry of {@code wants}, in the same order (0 = none)
     */
    public static long[] padPlan(long availableFe, List<Want> wants) {
        long[] out = new long[wants.size()];
        long left = Math.max(0L, availableFe);
        int docked = -1;
        for (int i = 0; i < wants.size(); i++) {
            if (wants.get(i).docked()) {
                docked = i;
                break;
            }
        }
        if (docked >= 0) {
            long give = Math.min(left, Math.min(PAD_DOCKED_FE_PER_TICK, Math.max(0L, wants.get(docked).missingFe())));
            out[docked] = give;
            left -= give;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < wants.size(); i++) {
            Want want = wants.get(i);
            if (i != docked && want.distance() <= PAD_NEARBY_RANGE && want.missingFe() > 0) {
                order.add(i);
            }
        }
        order.sort((a, b) -> Double.compare(wants.get(a).distance(), wants.get(b).distance()));
        int served = 0;
        for (int index : order) {
            if (served >= PAD_NEARBY_MAX || left <= 0) {
                break;
            }
            long give = Math.min(left, Math.min(PAD_NEARBY_FE_PER_TICK, wants.get(index).missingFe()));
            out[index] = give;
            left -= give;
            served++;
        }
        return out;
    }

    /** FE a Bay gives each parked companion this tick, in slot order, limited by its buffer. */
    public static long[] bayPlan(long availableFe, long[] missingFe) {
        long[] out = new long[missingFe.length];
        long left = Math.max(0L, availableFe);
        for (int i = 0; i < missingFe.length && left > 0; i++) {
            long give = Math.min(left, Math.min(BAY_FE_PER_TICK, Math.max(0L, missingFe[i])));
            out[i] = give;
            left -= give;
        }
        return out;
    }

    /** FE one crank gives a companion missing {@code missingFe}. */
    public static long crank(long missingFe) {
        return Math.max(0L, Math.min(CRANK_FE, missingFe));
    }

    /** Whether a player who last cranked at {@code lastTick} may crank at {@code now}. */
    public static boolean crankReady(long lastTick, long now) {
        return now - lastTick >= CRANK_COOLDOWN_TICKS;
    }

    // --- the Core boundary (NE <-> FE) ---------------------------------------------------------------

    /** FE available from {@code storedNe} at {@code neToFe} FE per NE. */
    public static long feAvailable(long storedNe, double neToFe) {
        return neToFe <= 0 ? 0L : (long) Math.floor(storedNe * neToFe);
    }

    /** NE to take from the buffer to deliver {@code fe} (rounded up, so the buffer never gives FE away). */
    public static long neCost(long fe, double neToFe) {
        return neToFe <= 0 ? Long.MAX_VALUE : (long) Math.ceil(fe / neToFe);
    }

    /** A buffer of {@code fe} FE expressed in NE (at least 1). */
    public static int bufferNe(long fe, double neToFe) {
        long ne = neToFe <= 0 ? fe : Math.round(fe / neToFe);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, ne));
    }

    /** Ticks to fill {@code missingFe} at {@code fePerTick} (0 when nothing is flowing). */
    public static long ticksToFull(long missingFe, long fePerTick) {
        return fePerTick <= 0 ? 0L : (missingFe + fePerTick - 1) / fePerTick;
    }
}
