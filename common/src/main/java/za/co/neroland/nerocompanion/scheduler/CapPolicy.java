package za.co.neroland.nerocompanion.scheduler;

import java.util.UUID;

import za.co.neroland.nerocompanion.authority.Decision;

/**
 * The deploy caps: {@code maxCompanionsPerPlayer} LIVE companions per owner (parked and
 * capsuled ones do not count) and {@code maxActiveBotsPerChunkRegion} LIVE companions of all owners per 8x8-chunk
 * region; plus the duplication guard (a companion id that is already LIVE cannot be deployed again).
 */
public final class CapPolicy {

    private CapPolicy() {
    }

    public static Decision canDeploy(LiveCompanionCounter live, UUID owner, UUID companionId, RegionKey region,
            CompanionLimits limits) {
        if (live.isLiveElsewhere(companionId, null)) {
            return Decision.deny("already_deployed");
        }
        if (live.liveFor(owner, companionId) >= limits.maxCompanionsPerPlayer()) {
            return Decision.deny("player_cap");
        }
        if (live.liveIn(region, companionId) >= limits.maxActiveBotsPerChunkRegion()) {
            return Decision.deny("region_cap");
        }
        return Decision.ALLOW;
    }
}
