package za.co.neroland.nerocompanion.mining;

/**
 * Progress-based "am I stuck walking there?", pure. Fed once per repath (every 10 ticks) with whether the
 * navigator found a path and how far the bot is from its goal. Any real progress (closer by half a block, or moved
 * half a block) resets it. No path twice in a row, or {@link #NO_PROGRESS_LIMIT} checks without progress, means
 * <b>unreachable now</b>. Separately counts consecutive unreachable targets so a job can give up
 * ({@link #GIVE_UP_AFTER}) instead of walking the whole area block by block.
 */
public final class StuckTracker {

    public static final int NO_PATH_LIMIT = 2;
    public static final int NO_PROGRESS_LIMIT = 6;
    public static final int GIVE_UP_AFTER = 8;
    private static final double PROGRESS = 0.5D;

    private double bestDistance = Double.MAX_VALUE;
    private int noPath;
    private int noProgress;
    private int unreachableInARow;

    /** A new walk target. */
    public void reset() {
        this.bestDistance = Double.MAX_VALUE;
        this.noPath = 0;
        this.noProgress = 0;
    }

    /** One check. Returns true when the target counts as unreachable now. */
    public boolean check(boolean pathFound, double distance, double movedSinceLast) {
        this.noPath = pathFound ? 0 : this.noPath + 1;
        if (distance < this.bestDistance - PROGRESS || movedSinceLast > PROGRESS) {
            this.noProgress = 0;
        } else {
            this.noProgress++;
        }
        this.bestDistance = Math.min(this.bestDistance, distance);
        return this.noPath >= NO_PATH_LIMIT || this.noProgress >= NO_PROGRESS_LIMIT;
    }

    /** Records an unreachable target; true once {@link #GIVE_UP_AFTER} happened in a row. */
    public boolean unreachable() {
        this.unreachableInARow++;
        return this.unreachableInARow >= GIVE_UP_AFTER;
    }

    /** A target was reached (block broken, deposit made): the give-up count starts over. */
    public void reached() {
        this.unreachableInARow = 0;
    }

    public int unreachableInARow() {
        return this.unreachableInARow;
    }
}
