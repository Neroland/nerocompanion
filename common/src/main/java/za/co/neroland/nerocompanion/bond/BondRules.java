package za.co.neroland.nerocompanion.bond;

/** How bond points are earned. All cosmetic: bond never changes stats. */
public final class BondRules {

    /** Owner must be online, in the same dimension and within this many blocks to count as "near". */
    public static final double NEAR_DISTANCE = 16.0D;
    /** Seconds near the owner (sampled once per 20-tick evaluation) that earn {@link #POINTS_PER_PRESENCE}. */
    public static final int PRESENCE_SECONDS = 20;
    public static final int POINTS_PER_PRESENCE = 1;
    /** Points for a pet (owner, empty main hand), at most once per {@link #PET_COOLDOWN_TICKS}. */
    public static final int POINTS_PER_PET = 5;
    public static final int PET_COOLDOWN_TICKS = 1200;
    /** Points for a finished unit of work, at most once per {@link #JOB_COOLDOWN_TICKS}. */
    public static final int POINTS_PER_JOB = 2;
    public static final int JOB_COOLDOWN_TICKS = 600;

    private BondRules() {
    }
}
