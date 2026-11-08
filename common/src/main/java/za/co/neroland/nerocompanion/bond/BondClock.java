package za.co.neroland.nerocompanion.bond;

/**
 * Per-companion bond accrual state. Every method takes the server game time and returns the points earned now; the
 * caller adds them to the companion. Time-based, so neither forced evaluations nor a fresh entity (recall/redeploy,
 * park/unpark) can speed it up: the server keeps one clock per companion id ({@code personality.Mood}). A stamp in
 * the future (another world's clock) counts as elapsed. Pure, so the maths is tested without a game.
 */
public final class BondClock {

    /** Game ticks one presence sample stands for. */
    public static final int SAMPLE_TICKS = 20;

    private int secondsNear;
    private long lastSample = Long.MIN_VALUE;
    private long lastPet = Long.MIN_VALUE;
    private long lastJob = Long.MIN_VALUE;

    /**
     * A presence sample at {@code now}. Counts at most once per {@link #SAMPLE_TICKS} of game time however often it
     * is called. {@code near}: owner online, same dimension, within {@link BondRules#NEAR_DISTANCE}.
     */
    public int sample(boolean near, long now) {
        if (!elapsed(now, this.lastSample, SAMPLE_TICKS)) {
            return 0;
        }
        this.lastSample = now;
        if (!near) {
            return 0;
        }
        this.secondsNear++;
        if (this.secondsNear >= BondRules.PRESENCE_SECONDS) {
            this.secondsNear = 0;
            return BondRules.POINTS_PER_PRESENCE;
        }
        return 0;
    }

    /** A pet at game time {@code now}: points if off cooldown, else 0 (the pet still gets a reaction). */
    public int pet(long now) {
        if (!this.petReady(now)) {
            return 0;
        }
        this.lastPet = now;
        return BondRules.POINTS_PER_PET;
    }

    /** Whether a pet at {@code now} would earn points (no state change). */
    public boolean petReady(long now) {
        return elapsed(now, this.lastPet, BondRules.PET_COOLDOWN_TICKS);
    }

    /** A finished job at game time {@code now}: points if off cooldown, else 0. */
    public int job(long now) {
        if (!elapsed(now, this.lastJob, BondRules.JOB_COOLDOWN_TICKS)) {
            return 0;
        }
        this.lastJob = now;
        return BondRules.POINTS_PER_JOB;
    }

    public int secondsNear() {
        return this.secondsNear;
    }

    static boolean elapsed(long now, long last, int ticks) {
        return last == Long.MIN_VALUE || now < last || now - last >= ticks;
    }
}
