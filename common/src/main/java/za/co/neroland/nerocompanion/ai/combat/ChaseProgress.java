package za.co.neroland.nerocompanion.ai.combat;

/**
 * Tracks whether a chase is getting anywhere, pure. The melee goal feeds it the squared distance to the target each
 * tick it is out of reach; once {@link GuardRules#GIVE_UP_TICKS} pass without the bot getting
 * {@link GuardRules#PROGRESS_SQ} closer (a wall, a roof, a target under water), the target is given up instead of
 * staring at it and re-pathing for its whole life.
 */
public final class ChaseProgress {

    private double best = Double.MAX_VALUE;
    private int stalled;

    /** Starts over (new target, or a hit landed). */
    public void reset() {
        this.best = Double.MAX_VALUE;
        this.stalled = 0;
    }

    /**
     * One out-of-reach tick at {@code distanceSq}.
     *
     * @return true when the chase should be given up
     */
    public boolean update(double distanceSq) {
        if (distanceSq < this.best - GuardRules.PROGRESS_SQ) {
            this.best = distanceSq;
            this.stalled = 0;
            return false;
        }
        this.stalled++;
        return this.stalled >= GuardRules.GIVE_UP_TICKS;
    }
}
