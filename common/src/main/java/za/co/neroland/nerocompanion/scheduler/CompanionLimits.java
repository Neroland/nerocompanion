package za.co.neroland.nerocompanion.scheduler;

/**
 * The numeric server rules the scheduler and the caps read, as one value so the logic is unit-testable without
 * the config system. Built from {@code NeroCompanionConfig} at runtime.
 */
public record CompanionLimits(
        int maxCompanionsPerPlayer,
        int maxActiveBotsPerChunkRegion,
        int throttleDistance,
        int sleepDistance,
        int lowBatteryPercent,
        int returnHomeRange) {

    /** Decision 001/003 defaults. */
    public static final CompanionLimits DEFAULTS = new CompanionLimits(4, 16, 32, 64, 10, 96);
}
