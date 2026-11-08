package za.co.neroland.nerocompanion.scout;

/**
 * When a scout starts a new ore scan (pure). Hostiles are rescanned every {@value #HOSTILE_TICKS} ticks (they move);
 * ores at most every {@value #ORE_TICKS} ticks (= the mark TTL), and only if the drone moved to another block, the
 * scan radius changed, or a cached ore is gone (mined) - or, as a refresh for blocks placed since, once
 * {@value #REFRESH_TICKS} ticks have passed. A started scan is time-sliced: {@value #SECTIONS_PER_STEP} chunk sections
 * per {@value #HOSTILE_TICKS}-tick step, so no single tick walks the whole sphere.
 */
public final class ScanCadence {

    public static final int HOSTILE_TICKS = 10;
    public static final int ORE_TICKS = 40;
    public static final int REFRESH_TICKS = 200;
    public static final int SECTIONS_PER_STEP = 4;
    private static final long NEVER = Long.MIN_VALUE / 4;

    private long lastStart = NEVER;
    private long anchor;
    private int anchorRadius = -1;

    /**
     * Whether to start a new ore scan at game tick {@code now} for a drone at packed block position {@code center}.
     *
     * @param cacheInvalid a cached ore is no longer ore (mined) or no scan has finished yet
     */
    public boolean shouldStart(long now, long center, int radius, boolean cacheInvalid) {
        long since = now - this.lastStart;
        if (since < ORE_TICKS) {
            return false;
        }
        boolean moved = this.anchorRadius < 0 || this.anchor != center || this.anchorRadius != radius;
        return moved || cacheInvalid || since >= REFRESH_TICKS;
    }

    /** A scan was started at {@code now} around {@code center}. */
    public void started(long now, long center, int radius) {
        this.lastStart = now;
        this.anchor = center;
        this.anchorRadius = radius;
    }

    /** Forget everything (goal restarted). */
    public void reset() {
        this.lastStart = NEVER;
        this.anchorRadius = -1;
    }
}
