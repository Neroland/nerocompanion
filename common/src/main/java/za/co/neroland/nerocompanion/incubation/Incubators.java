package za.co.neroland.nerocompanion.incubation;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.scheduler.CapPolicy;
import za.co.neroland.nerocompanion.scheduler.RegionKey;
import za.co.neroland.nerocompanion.charge.ChargingPadBlock;
import za.co.neroland.nerocompanion.charge.CompanionBayBlock;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.item.CompanionDeployer;
import za.co.neroland.nerocompanion.item.ScrapEggItem;

/**
 * Scrap Egg incubation on Charging Pads and Companion Bays. Server only.
 *
 * <ul>
 *   <li><b>Place</b> ({@link #place}, from {@link ScrapEggItem}): one egg per incubator, at most
 *       {@value #MAX_EGGS_PER_PLAYER} per player; the placer is the hatchling's owner (stored UUID, never proximity).</li>
 *   <li><b>Incubate</b> ({@link #tick}): the Pad's and Bay's block entities call it once per charging period with the
 *       ticks that passed; {@value Incubation#HATCH_TICKS} loaded ticks hatch the egg. Needs no power: an incubator
 *       is a warm, safe place, not a fuel sink.</li>
 *   <li><b>Hatch</b>: a fresh {@code nerocompanion:scrapling} record owned by the placer is deployed on the incubator
 *       through {@link CompanionDeployer} (same caps as a Capsule), homed to it. The owner must be online (the deploy
 *       path needs them); until then, or while their companion cap is full, the egg waits, ready, and retries every
 *       {@value #RETRY_TICKS} ticks; the owner is told once per egg that there is no room.</li>
 *   <li><b>Check / take back</b> ({@link #onEmptyHandUse}): the owner's empty-hand sneak-use shows progress, a second
 *       one takes the egg out with its progress.</li>
 *   <li><b>Incubator gone</b> ({@link #incubatorRemoved}): the egg drops as an item that keeps its progress (not its
 *       owner); whoever places it next owns what hatches.</li>
 * </ul>
 */
public final class Incubators {

    public static final Identifier SCRAPLING_TYPE = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
            "scrapling");
    public static final int MAX_EGGS_PER_PLAYER = 4;
    public static final int RETRY_TICKS = 600;

    /** Outcome of a placement, as a lang key for the action bar. */
    public enum Placed {
        PLACED("message.nerocompanion.scrap_egg.placed"),
        PROGRESS("message.nerocompanion.scrap_egg.progress"),
        OCCUPIED("message.nerocompanion.scrap_egg.occupied"),
        TOO_MANY("message.nerocompanion.scrap_egg.too_many"),
        NOT_ALLOWED("message.nerocompanion.scrap_egg.not_allowed"),
        NOT_INCUBATOR("message.nerocompanion.scrap_egg.not_incubator");

        private final String key;

        Placed(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public boolean consumed() {
            return this == PLACED;
        }
    }

    private static WeakReference<MinecraftServer> cachedServer = new WeakReference<>(null);
    @Nullable
    private static IncubatorStore cachedStore;
    /** Ready eggs whose hatch failed (owner offline, cap full): next game time to try. Not saved. */
    private static final Map<HomePos, Long> NEXT_TRY = new HashMap<>();
    /** Ready eggs whose owner has been told there is no room (told once per egg per server run). Not saved. */
    private static final Set<HomePos> TOLD = new HashSet<>();
    /** Last empty-hand check per player: where and when (for the take-back confirm). Not saved. */
    private static final Map<UUID, Prompt> PROMPTS = new HashMap<>();

    private record Prompt(HomePos at, long time) {
    }

    private Incubators() {
    }

    /** Whether {@code state} is a Scrap Egg incubator (Charging Pad or Companion Bay). */
    public static boolean isIncubator(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ChargingPadBlock || block instanceof CompanionBayBlock;
    }

    private static synchronized IncubatorStore store(MinecraftServer server) {
        if (cachedServer.get() != server || cachedStore == null) {
            cachedServer = new WeakReference<>(server);
            cachedStore = IncubatorStore.get(server);
            NEXT_TRY.clear();
            TOLD.clear();
            PROMPTS.clear();
        }
        return cachedStore;
    }

    /** The incubation at {@code pos}, if an egg sits there. */
    public static Optional<Incubation> at(ServerLevel level, BlockPos pos) {
        return store(level.getServer()).at(HomePos.of(level, pos));
    }

    /**
     * {@code player} sets {@code egg} (one item) into the incubator at {@code pos}. On {@link Placed#PLACED} the caller
     * shrinks the stack. Another egg there reports its progress (the owner's) or that the spot is taken.
     */
    public static Placed place(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack egg) {
        if (!isIncubator(level.getBlockState(pos))) {
            return Placed.NOT_INCUBATOR;
        }
        IncubatorStore store = store(level.getServer());
        HomePos at = HomePos.of(level, pos);
        Optional<Incubation> existing = store.at(at);
        if (existing.isPresent()) {
            return existing.get().owner().equals(player.getUUID()) ? Placed.PROGRESS : Placed.OCCUPIED;
        }
        // Only on a Pad or Bay the player may use themself (claims, spawn protection, adventure mode).
        if (za.co.neroland.nerocompanion.authority.OwnerAuthority.playerMayUse(player, level, pos).denied()) {
            return Placed.NOT_ALLOWED;
        }
        sweepOrphans(level.getServer(), player.getUUID());
        if (store.placedBy(player.getUUID()).size() >= MAX_EGGS_PER_PLAYER) {
            return Placed.TOO_MANY;
        }
        store.put(at, Incubation.start(player.getUUID(), ScrapEggItem.carriedTicks(egg)));
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.7F);
        return Placed.PLACED;
    }

    /**
     * Incubator tick: the Pad's or Bay's block entity at {@code pos} calls this from its server tick with the ticks
     * since its last call (its charging period). Cheap when no egg sits there: one map lookup.
     */
    public static void tick(ServerLevel level, BlockPos pos, int ticks) {
        IncubatorStore store = store(level.getServer());
        if (store.isEmpty()) {
            return;
        }
        HomePos at = HomePos.of(level, pos);
        Incubation egg = store.at(at).orElse(null);
        if (egg == null) {
            return;
        }
        if (!egg.ready()) {
            Incubation next = egg.advance(ticks);
            store.put(at, next);
            if (!next.ready()) {
                return;
            }
            egg = next;
        }
        long now = level.getGameTime();
        Long next = NEXT_TRY.get(at);
        if (next != null && now < next) {
            return;
        }
        if (!hatch(level, pos, at, egg)) {
            NEXT_TRY.put(at, now + RETRY_TICKS);
        }
    }

    /** Tries to hatch a ready egg; true when it hatched (the entry is gone). */
    private static boolean hatch(ServerLevel level, BlockPos pos, HomePos at, Incubation egg) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(egg.owner());
        if (owner == null) {
            return false;
        }
        CompanionRecord record = CompanionDeployer.freshRecord(level, SCRAPLING_TYPE, owner);
        // Check the caps here so a full squad gets one quiet line per egg, not the deploy refusal every 30 s.
        RegionKey region = RegionKey.ofBlock(level.dimension().identifier().toString(), pos.getX(), pos.getZ());
        Decision caps = CapPolicy.canDeploy(za.co.neroland.nerocompanion.recovery.Recovery.caps(level.getServer()),
                owner.getUUID(),
                record.companionId(), region, NeroCompanionConfig.limits());
        if (caps.denied()) {
            if (TOLD.add(at)) {
                owner.sendSystemMessage(Component.translatable("message.nerocompanion.scrap_egg.waiting"), true);
            }
            return false;
        }
        CompanionDeployer.Result result = CompanionDeployer.tryDeploy(level, owner, pos.above(), record, false);
        CompanionEntity scrapling = result.companion();
        if (scrapling == null) {
            return false;
        }
        scrapling.setHome(at);
        store(level.getServer()).remove(at);
        NEXT_TRY.remove(at);
        TOLD.remove(at);
        level.playSound(null, pos, za.co.neroland.nerocompanion.registry.ModSounds.SCRAPLING_HATCH.get(), SoundSource.NEUTRAL, 0.8F,
                za.co.neroland.nerocompanion.registry.ModSounds.pitch(level.getRandom()));
        level.sendParticles(za.co.neroland.nerocompanion.registry.ModParticles.SCRAP_PUFF.get(), pos.getX() + 0.5D,
                pos.getY() + 1.1D, pos.getZ() + 0.5D, 3, 0.2D, 0.1D, 0.2D, 0.01D);
        // The empty shell stays behind: the Scrap Bucket hat's unlock item (010 section 6.3).
        net.minecraft.world.level.block.Block.popResource(level, pos.above(),
                new net.minecraft.world.item.ItemStack(za.co.neroland.nerocompanion.registry.ModItems.SCRAP_SHELL.get()));
        owner.sendSystemMessage(Component.translatable("message.nerocompanion.scrap_egg.hatched"), true);
        za.co.neroland.nerocompanion.progression.CompanionProgress.hatched(level.getServer(), owner.getUUID());
        return true;
    }

    /**
     * {@code player} sneak-used the incubator at {@code pos} with an empty hand (the Pad's and Bay's
     * {@code useWithoutItem} call this). Their own egg: the first use shows its progress, a second within
     * {@value EggPrompts#CONFIRM_TICKS} ticks takes it out (an egg item that keeps its progress). Someone else's egg:
     * if the player may use the block under the claim rules, the same two-step use pops it out as an egg item that
     * keeps its progress but not its owner (an unwanted egg on your Pad never needs the block broken); otherwise
     * "occupied". No egg: false, and the block does what it normally does.
     */
    public static boolean onEmptyHandUse(ServerLevel level, BlockPos pos, ServerPlayer player) {
        IncubatorStore store = store(level.getServer());
        if (store.isEmpty()) {
            return false;
        }
        HomePos at = HomePos.of(level, pos);
        Incubation egg = store.at(at).orElse(null);
        if (egg == null) {
            return false;
        }
        long now = level.getGameTime();
        if (!egg.owner().equals(player.getUUID())) {
            if (za.co.neroland.nerocompanion.authority.OwnerAuthority.playerMayUse(player, level, pos).denied()) {
                player.sendSystemMessage(Component.translatable(Placed.OCCUPIED.key()), true);
                return true;
            }
            Prompt prior = PROMPTS.remove(player.getUUID());
            if (prior != null && EggPrompts.confirms(prior.at(), prior.time(), at, now)) {
                store.remove(at);
                NEXT_TRY.remove(at);
                TOLD.remove(at);
                net.minecraft.world.level.block.Block.popResource(level, pos.above(),
                        ScrapEggItem.withTicks(ScrapEggItem.newStack(), egg.elapsedTicks()));
                player.sendSystemMessage(Component.translatable("message.nerocompanion.scrap_egg.evicted"), true);
                return true;
            }
            PROMPTS.put(player.getUUID(), new Prompt(at, now));
            player.sendSystemMessage(Component.translatable("message.nerocompanion.scrap_egg.evict_confirm"), true);
            return true;
        }
        Prompt last = PROMPTS.remove(player.getUUID());
        if (last != null && EggPrompts.confirms(last.at(), last.time(), at, now)) {
            store.remove(at);
            NEXT_TRY.remove(at);
            TOLD.remove(at);
            ItemStack stack = ScrapEggItem.withTicks(ScrapEggItem.newStack(), egg.elapsedTicks());
            //? if >=26.3 {
            /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
            *///?} else {
            player.getInventory().placeItemBackInInventory(stack);
            //?}
            player.sendSystemMessage(Component.translatable("message.nerocompanion.scrap_egg.taken"), true);
            return true;
        }
        PROMPTS.put(player.getUUID(), new Prompt(at, now));
        player.sendSystemMessage(Component.translatable("message.nerocompanion.scrap_egg.inspect",
                Math.round(egg.progress() * 100.0F)), true);
        return true;
    }

    /**
     * The incubator at {@code pos} is being removed: its egg (if any) drops as an item with its progress. The Pad's
     * and Bay's removal paths call this; {@link #place} also sweeps the placer's eggs whose incubator vanished.
     */
    public static void incubatorRemoved(ServerLevel level, BlockPos pos) {
        IncubatorStore store = store(level.getServer());
        if (store.isEmpty()) {
            return;
        }
        HomePos at = HomePos.of(level, pos);
        store.remove(at).ifPresent(egg -> {
            NEXT_TRY.remove(at);
            TOLD.remove(at);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    ScrapEggItem.withTicks(ScrapEggItem.newStack(), egg.elapsedTicks()));
        });
    }

    /** Drops {@code owner}'s eggs whose incubator is gone, in loaded chunks only. */
    private static void sweepOrphans(MinecraftServer server, UUID owner) {
        List<HomePos> placed = store(server).placedBy(owner);
        for (HomePos at : placed) {
            for (ServerLevel level : server.getAllLevels()) {
                if (at.isIn(level) && level.isLoaded(at.pos()) && !isIncubator(level.getBlockState(at.pos()))) {
                    incubatorRemoved(level, at.pos());
                }
            }
        }
    }

    /** Erasure: {@code player}'s incubating eggs are forgotten (and so never hatch). */
    public static int forgetOwner(MinecraftServer server, UUID player) {
        IncubatorStore store = store(server);
        synchronized (Incubators.class) {
            for (HomePos at : store.placedBy(player)) {
                NEXT_TRY.remove(at);
                TOLD.remove(at);
            }
            PROMPTS.remove(player);
        }
        return store.forgetOwner(player);
    }

    /** Server stopped: drop the cached store and every in-memory note. */
    public static synchronized void serverStopped() {
        cachedServer = new WeakReference<>(null);
        cachedStore = null;
        NEXT_TRY.clear();
        TOLD.clear();
        PROMPTS.clear();
    }
}
