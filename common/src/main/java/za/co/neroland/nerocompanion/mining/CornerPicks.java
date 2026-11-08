package za.co.neroland.nerocompanion.mining;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * The two-corner mining selection made with the Remote. The first sneak-use stores a pending corner for that
 * owner and bot; a second one on the same bot, in the same dimension, within {@link #EXPIRY_TICKS}, completes the
 * pair. Anything else starts over. In memory only (never saved, never logged); keyed by the owner's UUID so one
 * player's corners can never complete another's.
 */
public final class CornerPicks {

    /** A pending first corner lasts one minute. */
    public static final long EXPIRY_TICKS = 1200L;

    /** A stored first corner. */
    public record Pending(UUID botId, Identifier dimension, BlockPos pos, long tick) {
    }

    /** Result of a pick: {@code first} is present only when this pick completed a pair. */
    public record Pick(boolean completed, Optional<BlockPos> first) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public Pick pick(UUID owner, UUID botId, Identifier dimension, BlockPos pos, long now) {
        Pending previous = this.pending.get(owner);
        if (previous != null && previous.botId().equals(botId) && previous.dimension().equals(dimension)
                && now - previous.tick() <= EXPIRY_TICKS && now >= previous.tick()) {
            this.pending.remove(owner);
            return new Pick(true, Optional.of(previous.pos()));
        }
        this.pending.put(owner, new Pending(botId, dimension, pos.immutable(), now));
        return new Pick(false, Optional.empty());
    }

    /** Server stopped: forget every pending pick. */
    public void clearAll() {
        this.pending.clear();
    }

    public void clear(UUID owner) {
        this.pending.remove(owner);
    }

    public Optional<Pending> pendingFor(UUID owner) {
        return Optional.ofNullable(this.pending.get(owner));
    }
}
