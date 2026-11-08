package za.co.neroland.nerocompanion.scheduler;

import java.util.UUID;

/**
 * Where the caps learn how many companions are LIVE. Answered by the Recovery Ledger
 * ({@code recovery.RecoveryBook}), which knows every companion's state (LIVE / CAPSULE / PARKED / LOST) and last-known
 * position across restarts - so the caps and the duplicate guard survive a server restart.
 */
public interface LiveCompanionCounter {

    /** LIVE companions owned by {@code owner}, not counting {@code except} (may be null). */
    int liveFor(UUID owner, UUID except);

    /** LIVE companions of all owners in {@code region}, not counting {@code except} (may be null). */
    int liveIn(RegionKey region, UUID except);

    /** Whether {@code companionId} is LIVE as some entity other than {@code entityId} (duplication guard). */
    boolean isLiveElsewhere(UUID companionId, UUID entityId);
}
