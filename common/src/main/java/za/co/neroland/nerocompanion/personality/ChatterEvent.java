package za.co.neroland.nerocompanion.personality;

/**
 * What prompted a chatter line. Each event has a base cooldown (ticks) and its own line subset per trait. Events are
 * server-side facts about the companion itself - never about another player.
 */
public enum ChatterEvent {
    /** Just deployed or loaded near its owner. */
    DEPLOY(200),
    /** Energy dropped to the low-battery threshold. */
    LOW_BATTERY(1200),
    /** It spotted ore (scout marks, mining). */
    ORE_FOUND(400),
    /** A unit of work finished. */
    JOB_DONE(600),
    /** The owner petted it (answers every pet it can, so it skips the shared gap). */
    PETTED(40, true),
    /** Nothing happening (rolled on the 20-tick evaluation). */
    IDLE(1200),
    /** A player other than the owner came close (grumpy and shy react). */
    STRANGER(600),
    /** Bond level went up. */
    BOND_UP(0, true),
    /** A trick or idle flourish (showoff, curious). */
    TRICK(400),
    /** A hostile is behind the owner (Scout Danger Alert): one shared line, same for every trait. */
    DANGER(200, true),
    /** A guard took a target (combat bot). */
    THREAT_SPOTTED(600),
    /** A guard's target went down to its hit (combat bot). */
    TARGET_DOWN(200);

    private final int baseCooldown;
    private final boolean skipsGap;

    ChatterEvent(int baseCooldown) {
        this(baseCooldown, false);
    }

    ChatterEvent(int baseCooldown, boolean skipsGap) {
        this.baseCooldown = baseCooldown;
        this.skipsGap = skipsGap;
    }

    /** Whether this event ignores the companion-wide gap (it still respects its own cooldown and silence). */
    public boolean skipsGap() {
        return this.skipsGap;
    }

    /** Minimum ticks between two lines for this event at normal chatter volume. */
    public int baseCooldown() {
        return this.baseCooldown;
    }
}
