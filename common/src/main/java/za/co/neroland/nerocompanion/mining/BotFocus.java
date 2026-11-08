package za.co.neroland.nerocompanion.mining;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Which of a player's bots a Remote sneak-use on a block (mining corner, container binding) applies to. The Remote
 * carries no selection, so:
 *
 * <ol>
 *   <li>the bot the owner <b>last interacted with</b> (any right-click on it, including opening its Companion screen
 *       with the Remote) within the last {@link #FOCUS_TICKS}, if it is still loaded, alive, of a wanted kind and
 *       within {@link #FOCUS_RANGE} blocks of the clicked block;</li>
 *   <li>else the <b>nearest</b> bot of the first wanted kind within {@link #SEARCH_RANGE} blocks of the player, then
 *       the next kind.</li>
 * </ol>
 *
 * Only bots whose stored owner UUID is the player are ever considered - never another player's. The focus map is
 * in memory only (session-local entity ids, never saved or logged).
 */
public final class BotFocus {

    public static final long FOCUS_TICKS = 6000L;
    public static final double FOCUS_RANGE = 48.0D;
    public static final double SEARCH_RANGE = 32.0D;

    private record Entry(int entityId, long tick) {
    }

    private static final Map<UUID, Entry> FOCUS = new ConcurrentHashMap<>();

    private BotFocus() {
    }

    /** Server: {@code owner} interacted with {@code bot}. The caller has already checked ownership. */
    public static void focus(Player owner, CompanionEntity bot) {
        FOCUS.put(owner.getUUID(), new Entry(bot.getId(), bot.level().getGameTime()));
    }

    /** Forgets a player's focus (logout, erasure). */
    public static void forget(UUID owner) {
        FOCUS.remove(owner);
    }

    /** Server stopped: no player's focus survives into another world. */
    public static void serverStopped() {
        FOCUS.clear();
    }

    /** Picks the bot a sneak-use at {@code clicked} applies to; {@code kinds} in order of preference. */
    public static Optional<CompanionEntity> pick(ServerPlayer player, ServerLevel level, BlockPos clicked,
            List<Class<? extends CompanionEntity>> kinds) {
        return pick(player, level, clicked, kinds, null);
    }

    /**
     * As {@link #pick(ServerPlayer, ServerLevel, BlockPos, List)}, but a still-usable bot with entity UUID
     * {@code preferred} (the bot a pending first corner belongs to) wins over focus and distance.
     */
    public static Optional<CompanionEntity> pick(ServerPlayer player, ServerLevel level, BlockPos clicked,
            List<Class<? extends CompanionEntity>> kinds, @Nullable UUID preferred) {
        if (preferred != null) {
            for (Class<? extends CompanionEntity> kind : kinds) {
                List<? extends CompanionEntity> found = level.getEntitiesOfClass(kind,
                        player.getBoundingBox().inflate(FOCUS_RANGE),
                        bot -> preferred.equals(bot.getUUID()) && usable(bot, player));
                if (!found.isEmpty()) {
                    return Optional.of(found.get(0));
                }
            }
        }
        Entry entry = FOCUS.get(player.getUUID());
        if (entry != null && level.getGameTime() - entry.tick() <= FOCUS_TICKS) {
            Entity entity = level.getEntity(entry.entityId());
            if (entity instanceof CompanionEntity bot && usable(bot, player) && isKind(bot, kinds)
                    && bot.position().distanceToSqr(Vec3.atCenterOf(clicked)) <= FOCUS_RANGE * FOCUS_RANGE) {
                return Optional.of(bot);
            }
        }
        for (Class<? extends CompanionEntity> kind : kinds) {
            List<? extends CompanionEntity> found = level.getEntitiesOfClass(kind,
                    player.getBoundingBox().inflate(SEARCH_RANGE), bot -> usable(bot, player));
            Optional<? extends CompanionEntity> nearest = found.stream()
                    .min(Comparator.comparingDouble(bot -> bot.distanceToSqr(player)));
            if (nearest.isPresent()) {
                return Optional.of(nearest.get());
            }
        }
        return Optional.empty();
    }

    private static boolean usable(CompanionEntity bot, Player player) {
        return bot.isAlive() && !bot.isRemoved() && !bot.isInert() && bot.isOwnedBy(player);
    }

    private static boolean isKind(CompanionEntity bot, List<Class<? extends CompanionEntity>> kinds) {
        for (Class<? extends CompanionEntity> kind : kinds) {
            if (kind.isInstance(bot)) {
                return true;
            }
        }
        return false;
    }
}
