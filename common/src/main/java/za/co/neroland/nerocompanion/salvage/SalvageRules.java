package za.co.neroland.nerocompanion.salvage;

/** The death-salvage switches as pure functions. */
public final class SalvageRules {

    /** A gatherer must be within this many blocks of the owner's death position. */
    public static final double GATHER_RANGE = 24.0D;
    /** Picked up within this distance (blocks). */
    public static final double PICKUP_RANGE = 1.6D;

    private SalvageRules() {
    }

    /**
     * Whether a death opens a window: whenever drops fall ({@code keepInventory} off). The window is opened even with
     * salvage turned off, so the drops stay marked and other players' companions leave them alone; turning salvage
     * off only stops the gathering ({@link #gathers}).
     */
    public static boolean opensWindow(boolean keepInventory) {
        return !keepInventory;
    }

    /**
     * Whether a companion gathers: salvage on, and either an active Salvage Chip or a chassis that salvages by
     * default (carry bots) while that default is on.
     */
    public static boolean gathers(boolean enabled, boolean carryBotsByDefault, boolean salvagesByDefault,
            boolean hasSalvageChip) {
        return enabled && (hasSalvageChip || (carryBotsByDefault && salvagesByDefault));
    }

    /** Salvage keeps a companion awake only while its owner is online (offline owners' companions sleep). */
    public static boolean keepsAwake(boolean ownerOnline, boolean windowOpen, boolean inRange) {
        return ownerOnline && windowOpen && inRange;
    }
}
