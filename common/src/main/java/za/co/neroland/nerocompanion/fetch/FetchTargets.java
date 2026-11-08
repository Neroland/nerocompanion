package za.co.neroland.nerocompanion.fetch;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Landed Fetch Balls waiting to be fetched: the ball's item entity -> who threw it and when. Server memory only
 * (never saved, never sent); entries expire after {@value #TTL_TICKS} ticks. Stage 3's fetch goal reads it and
 * ignores a ball thrown by anyone but the pet's owner.
 */
public final class FetchTargets {

    public static final long TTL_TICKS = 1200L;

    public record Target(UUID thrower, long landedAt) {
    }

    private static final Map<UUID, Target> TARGETS = new HashMap<>();

    private FetchTargets() {
    }

    public static synchronized void landed(UUID itemEntity, UUID thrower, long gameTime) {
        if (TARGETS.size() > 512) {
            TARGETS.values().removeIf(t -> gameTime - t.landedAt() > TTL_TICKS);
        }
        TARGETS.put(itemEntity, new Target(thrower, gameTime));
    }

    public static synchronized Optional<Target> get(UUID itemEntity, long gameTime) {
        Target target = TARGETS.get(itemEntity);
        if (target != null && gameTime - target.landedAt() > TTL_TICKS) {
            TARGETS.remove(itemEntity);
            return Optional.empty();
        }
        return Optional.ofNullable(target);
    }

    public static synchronized void remove(UUID itemEntity) {
        TARGETS.remove(itemEntity);
    }

    public static synchronized void serverStopped() {
        TARGETS.clear();
    }
}
