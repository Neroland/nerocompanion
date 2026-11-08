package za.co.neroland.nerocompanion.salvage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.alert.AlertText;
import za.co.neroland.nerocompanion.alert.CompanionAlerts;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Death salvage in the game: sees deaths on the server tick, opens {@link DeathWindows}, lets eligible
 * companions gather into the {@link SalvageHold}, hands it back when the owner interacts with a holder, and drops it
 * at the holder when it expires. Server thread only. The windows and the "seen dead" set live in memory and are
 * cleared on server stop and on erasure.
 */
public final class Salvage {

    private static final DeathWindows WINDOWS = new DeathWindows();
    private static final Set<UUID> DEAD_SEEN = new HashSet<>();
    /** A player this young (ticks since their entity was created, i.e. since joining or respawning) is not dying now. */
    private static final int JOIN_GRACE_TICKS = 20;

    private Salvage() {
    }

    /** Game time used by salvage (the overworld clock, like the ledger). */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Each server tick (end): see fresh deaths; every 5 s, expire windows and forget players who left. */
    public static synchronized void tick(MinecraftServer server) {
        long now = now(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (player.isDeadOrDying()) {
                // A player who reconnects while dead is dead again on their first tick: that is not a new death.
                if (DEAD_SEEN.add(id) && player.tickCount > JOIN_GRACE_TICKS) {
                    onDeath(player, now);
                }
            } else {
                DEAD_SEEN.remove(id);
            }
        }
        if (now % 100L == 0L) {
            WINDOWS.expire(now);
            Set<UUID> online = new HashSet<>();
            server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
            DEAD_SEEN.retainAll(online);
        }
    }

    private static void onDeath(ServerPlayer player, long now) {
        NeroCompanionConfig.Salvage config = NeroCompanionConfig.salvage();
        ServerLevel level = player.level();
        if (!SalvageRules.opensWindow(keepInventory(level))) {
            WINDOWS.forget(player.getUUID());
            return;
        }
        String dimension = level.dimension().identifier().toString();
        DeathWindows.Window window = WINDOWS.open(player.getUUID(), dimension, player.getX(), player.getY(),
                player.getZ(), now, config.windowTicks());
        double r = DeathWindows.H_RADIUS;
        AABB box = new AABB(player.getX() - r, player.getY() - DeathWindows.BELOW, player.getZ() - r,
                player.getX() + r, player.getY() + DeathWindows.ABOVE, player.getZ() + r);
        List<DeathWindows.Candidate> candidates = new ArrayList<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box,
                item -> item.isAlive() && item.tickCount <= DeathWindows.MAX_AGE)) {
            candidates.add(new DeathWindows.Candidate(item.getUUID(), dimension, item.getX(), item.getY(), item.getZ(),
                    item.tickCount, item.getOwner() != null, item.getItem().getCount()));
        }
        WINDOWS.releaseContested(window, candidates, now);
        WINDOWS.claim(window, candidates, now);
    }

    private static boolean keepInventory(ServerLevel level) {
        //? if >=1.21.11 {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY);
        //?} else {
        /*return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        *///?}
    }

    // =================================================================================================
    // Companions
    // =================================================================================================

    /** Whether {@code companion} salvages at all (config, chassis default, Salvage chip). */
    public static boolean gathers(CompanionEntity companion) {
        NeroCompanionConfig.Salvage config = NeroCompanionConfig.salvage();
        boolean chip = companion.activeChips().stream().anyMatch(c -> c.chip().equals(ChipKinds.SALVAGE.id()));
        return SalvageRules.gathers(config.enabled(), config.carryBotsByDefault(), companion.salvagesByDefault(),
                chip);
    }

    /** The owner's open window, if {@code companion} may gather from it (dimension, range, eligibility). */
    public static synchronized Optional<DeathWindows.Window> windowFor(CompanionEntity companion) {
        UUID owner = companion.ownerId();
        if (owner == null || !(companion.level() instanceof ServerLevel level) || companion.isInert()) {
            return Optional.empty();
        }
        Optional<DeathWindows.Window> window = WINDOWS.active(owner, now(level.getServer()));
        if (window.isEmpty() || window.get().items().isEmpty()) {
            return Optional.empty();
        }
        String dimension = level.dimension().identifier().toString();
        if (!window.get().near(dimension, companion.getX(), companion.getY(), companion.getZ(),
                SalvageRules.GATHER_RANGE) || !gathers(companion)
                || SalvageHold.book(level.getServer()).isFull(owner)) {
            return Optional.empty();
        }
        return window;
    }

    /** Whether salvage keeps {@code companion} awake right now (the scheduler asks once a second). */
    public static boolean keepsAwake(CompanionEntity companion, boolean ownerOnline) {
        return ownerOnline && windowFor(companion).isPresent();
    }

    /** The nearest live death drop of the owner that {@code companion} can reach, or null. */
    @Nullable
    public static synchronized ItemEntity nearestTarget(CompanionEntity companion) {
        Optional<DeathWindows.Window> window = windowFor(companion);
        if (window.isEmpty() || !(companion.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID owner = window.get().owner();
        ItemEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (UUID id : window.get().items()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) {
                WINDOWS.release(owner, id);
                continue;
            }
            double d = companion.distanceToSqr(item);
            if (d < bestSq && d <= SalvageRules.GATHER_RANGE * SalvageRules.GATHER_RANGE * 4.0D) {
                best = item;
                bestSq = d;
            }
        }
        return best;
    }

    /** Whether {@code item} is still one of the owner's death drops. */
    public static synchronized boolean isOwnersDrop(CompanionEntity companion, ItemEntity item) {
        UUID owner = companion.ownerId();
        return owner != null && item.isAlive() && WINDOWS.isOwners(owner, item.getUUID());
    }

    /**
     * Whether {@code item} is another player's death drop whose window is still open. Ordinary pickups (carry bots,
     * the drone's carry-lite) refuse these; once the window closes, vanilla rules apply again.
     */
    public static synchronized boolean isAnotherPlayersDrop(CompanionEntity companion, ItemEntity item) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return false;
        }
        return WINDOWS.isClaimedByOther(companion.ownerId(), item.getUUID(), now(level.getServer()));
    }

    /** {@code companion} reached {@code item}: into the hold (or left lying when the hold is full). */
    public static synchronized boolean pickUp(CompanionEntity companion, ItemEntity item) {
        UUID owner = companion.ownerId();
        UUID holder = companion.companionId();
        if (owner == null || holder == null || !(companion.level() instanceof ServerLevel level)
                || !isOwnersDrop(companion, item)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        HoldBook<ItemStack> hold = SalvageHold.book(server);
        boolean first = !hold.holdsFor(owner);
        long holdTicks = NeroCompanionConfig.salvage().holdMinutes() * 60L * 20L;
        ItemStack lying = item.getItem();
        // Never more than was claimed at the death: a foreign stack vanilla merged into this one stays on the ground.
        int take = DeathWindows.takeCount(WINDOWS.claimedCount(owner, item.getUUID()), lying.getCount());
        if (take <= 0) {
            WINDOWS.release(owner, item.getUUID());
            return false;
        }
        ItemStack taken = lying.copy();
        taken.setCount(take);
        if (!hold.add(owner, holder, taken, now(server), holdTicks, System.currentTimeMillis())) {
            return false;
        }
        WINDOWS.release(owner, item.getUUID());
        companion.level().playSound(null, companion.getX(), companion.getY(), companion.getZ(),
                za.co.neroland.nerocompanion.registry.ModSounds.SALVAGE_CHIME.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F,
                za.co.neroland.nerocompanion.registry.ModSounds.pitch(companion.getRandom()));
        if (take >= lying.getCount()) {
            item.discard();
        } else {
            ItemStack rest = lying.copy();
            rest.shrink(take);
            item.setItem(rest);
        }
        if (first) {
            long ticksLeft = hold.get(owner).map(h -> Math.max(0L, h.expiresAt() - now(server))).orElse(holdTicks);
            CompanionAlerts.raise(server, owner, AlertText.Kind.SALVAGE_HELD, holder, companion.typeId(),
                    companion.companionName(), java.util.OptionalInt.of((int) Math.min(Integer.MAX_VALUE,
                            (ticksLeft + 1_199L) / 1_200L)), java.util.OptionalInt.empty());
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.nerocompanion.salvage.held",
                        companion.getDisplayName()), true);
            }
        }
        return true;
    }

    /**
     * The owner interacted with {@code companion}: if it holds their salvage, everything goes to them (inventory,
     * the rest at their feet).
     *
     * @return whether anything was returned
     */
    public static boolean returnTo(CompanionEntity companion, Player owner) {
        UUID holder = companion.companionId();
        if (holder == null || !(companion.level() instanceof ServerLevel level)) {
            return false;
        }
        List<ItemStack> items = SalvageHold.book(level.getServer()).returnTo(owner.getUUID(), holder);
        if (items.isEmpty()) {
            return false;
        }
        for (ItemStack stack : items) {
            //? if >=26.3 {
            /*owner.getInventory().placeItemBackInInventory(stack.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
            *///?} else {
            owner.getInventory().placeItemBackInInventory(stack.copy());
            //?}
        }
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), za.co.neroland.nerocompanion.registry.ModSounds.SALVAGE_RETURN.get(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
        if (owner instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.nerocompanion.salvage.returned",
                    companion.getDisplayName()), true);
        }
        return true;
    }

    /** {@code companion} is evaluated: an expired hold it took part in drops at its feet. */
    public static void dropIfExpired(CompanionEntity companion) {
        UUID owner = companion.ownerId();
        UUID holder = companion.companionId();
        if (owner == null || holder == null || !(companion.level() instanceof ServerLevel level)) {
            return;
        }
        HoldBook<ItemStack> book = SalvageHold.book(level.getServer());
        if (!book.heldBy(owner, holder) || level.getServer().getPlayerList().getPlayer(owner) == null) {
            // Dropped only while the owner is online: an offline owner's things wait for them (never dropped
            // unattended to despawn).
            return;
        }
        for (ItemStack stack : book.takeExpired(owner, holder, now(level.getServer()))) {
            ItemEntity item = new ItemEntity(level, companion.getX(), companion.getY() + 0.25D, companion.getZ(),
                    stack.copy());
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    /** Erasure: forget the owner's window and death flag (the hold is erased with the stores). */
    public static synchronized void forgetOwner(UUID owner) {
        WINDOWS.forget(owner);
        DEAD_SEEN.remove(owner);
    }

    /** Whether memory still holds anything about {@code owner} (erasure probe). */
    public static synchronized boolean holdsInMemory(UUID owner) {
        return WINDOWS.holds(owner) || DEAD_SEEN.contains(owner);
    }

    public static synchronized void serverStopped() {
        WINDOWS.clear();
        DEAD_SEEN.clear();
    }
}
