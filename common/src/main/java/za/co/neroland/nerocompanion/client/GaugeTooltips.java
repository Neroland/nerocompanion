package za.co.neroland.nerocompanion.client;

import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * The gauge tooltips for the NeroCompanion screens: each bar names itself, gives amount / capacity with
 * units, and its rate. Builders append lines to a list; the screen hands the list to its tooltip queue.
 * Numbers come from {@link GaugeMath}; every word is an English source lang key. Only the viewer's own
 * companion and Bay numbers already on screen — no names of other players, no positions.
 */
public final class GaugeTooltips {

    /** Percent change per minute treated as steady (sync rounding). */
    public static final double PERCENT_DEADBAND_PER_MINUTE = 0.05D;

    private GaugeTooltips() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("nerocompanion.gui.gauge." + key, args);
    }

    /**
     * A Bay's buffer: name, stored / capacity in FE (and %), and the net change per tick measured on
     * the client, plus how many parked companions it is charging.
     */
    public static void buffer(List<Component> out, long stored, long capacity, double netPerTick, int charging) {
        out.add(tr("bay_power"));
        if (capacity <= 0) {
            out.add(tr("energy.no_buffer"));
            return;
        }
        out.add(tr("energy.amount", GaugeMath.compact(stored), GaugeMath.compact(capacity),
                GaugeMath.percent(stored, capacity)));
        out.add(tr("energy.net", GaugeMath.signedRate(netPerTick)));
        if (charging > 0) {
            out.add(tr("bay_power.charging", charging));
        }
        if (stored >= capacity) {
            out.add(tr("energy.full"));
        } else if (stored <= 0) {
            out.add(tr("energy.empty"));
        }
    }

    /**
     * A companion's battery with the server's known rates: charging at {@code chargingPerTick} FE/t, or
     * drawing {@code drainPerSecond} FE/s in {@code mode}; {@code asleep} means no drain. A companion
     * that is not loaded only has its last known percent ({@code loaded == false}).
     */
    public static void battery(List<Component> out, boolean loaded, long stored, long capacity, long chargingPerTick,
            long drainPerSecond, boolean asleep, Component mode) {
        out.add(tr("battery"));
        if (!loaded) {
            out.add(tr("battery.last_known", GaugeMath.percent(stored, capacity)));
            out.add(tr("battery.away"));
            return;
        }
        if (capacity <= 0) {
            out.add(tr("energy.no_buffer"));
            return;
        }
        out.add(tr("energy.amount", GaugeMath.compact(stored), GaugeMath.compact(capacity),
                GaugeMath.percent(stored, capacity)));
        if (chargingPerTick > 0) {
            out.add(tr("battery.charging", GaugeMath.rate(chargingPerTick)));
            if (stored < capacity) {
                out.add(tr("battery.full_in",
                        GaugeMath.duration((long) Math.ceil((capacity - stored) / (double) chargingPerTick))));
            }
        } else if (asleep || drainPerSecond <= 0) {
            out.add(tr("battery.no_drain"));
        } else {
            out.add(tr("battery.drain", GaugeMath.rate(drainPerSecond), mode));
            out.add(tr("battery.left", GaugeMath.duration(
                    (long) Math.ceil(stored / (double) drainPerSecond) * GaugeMath.TICKS_PER_SECOND)));
        }
        if (stored >= capacity) {
            out.add(tr("energy.full"));
        }
    }

    /**
     * A parked companion's charge bar: its name, percent charged, the charge rate in %/min measured on
     * the client, and the time to full at that rate.
     */
    public static void parkedCharge(List<Component> out, Component name, int percent, double percentPerTick,
            Component state) {
        out.add(name);
        out.add(tr("parked.amount", Math.max(0, Math.min(100, percent))));
        double perMinute = percentPerTick * GaugeMath.TICKS_PER_SECOND * 60D;
        if (percent < 100 && perMinute > PERCENT_DEADBAND_PER_MINUTE) {
            out.add(tr("parked.rate", GaugeMath.rate(perMinute)));
            long ticks = (long) Math.ceil((100 - percent) / percentPerTick);
            out.add(tr("parked.full_in", GaugeMath.duration(ticks)));
        } else {
            out.add(state);
        }
    }

    /** The bond pips: the points still to earn for the next level (nothing at the top level). */
    public static void bondPoints(List<Component> out, int level, int pointsToNext) {
        if (level < 4 && pointsToNext > 0) {
            out.add(tr("bond.next", GaugeMath.grouped(pointsToNext)));
        }
    }
}
