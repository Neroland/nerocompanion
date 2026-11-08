package za.co.neroland.nerocompanion.network;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipItem;
import za.co.neroland.nerocompanion.chip.ChipKind;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.chip.ChipValidation;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.Bond;
import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;
import za.co.neroland.nerocompanion.hud.HudBoard;
import za.co.neroland.nerocompanion.menu.CompanionMenu;
import za.co.neroland.nerocompanion.menu.MenuOpener;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.registry.ModItems;
import za.co.neroland.nerocompanion.scheduler.CompanionIndex;

/**
 * Server handlers for every client intent. Each one: drops FakePlayers, rate-limits (10 per second per player per
 * payload type, excess dropped silently), then checks that the sender owns the companion and that it is in the
 * state and range the action needs. Nothing the client says is trusted beyond "this is what I asked for".
 * Answers go back as an owner-only snapshot (when the screen is open) or one action-bar sentence.
 */
public final class ServerActions {

    /** Screen actions need the companion this close (the screen's AWAY threshold). */
    public static final double SCREEN_RANGE = 64.0D;

    private static final RateLimiter LIMITER = new RateLimiter();

    private ServerActions() {
    }

    public static void serverStopped() {
        LIMITER.clear();
    }

    private static boolean admit(ServerPlayer player, String type) {
        if (Services.ENTITIES.isFakePlayer(player)) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        return server != null && LIMITER.allow(player.getUUID(), type, server.getTickCount());
    }

    /** NeroLink: one action for {@code player} against the shared limiter's per-minute link bucket. */
    public static boolean admitLink(MinecraftServer server, UUID player) {
        return server != null && player != null && LIMITER.allowLink(player, server.getTickCount());
    }

    /** The loaded entity of a LIVE companion, in any dimension. */
    @Nullable
    public static CompanionEntity find(MinecraftServer server, UUID companionId) {
        Optional<UUID> entityId = CompanionIndex.forServer(server).entityOf(companionId);
        if (entityId.isEmpty()) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId.get());
            if (entity instanceof CompanionEntity companion && !companion.isRemoved()
                    && companionId.equals(companion.companionId())) {
                return companion;
            }
        }
        return null;
    }

    private static boolean near(ServerPlayer player, CompanionEntity companion) {
        return companion.level() == player.level() && companion.distanceToSqr(player) <= SCREEN_RANGE * SCREEN_RANGE;
    }

    /** Owner-checked lookup; tells the player why and returns null on any failure. */
    @Nullable
    private static CompanionEntity owned(ServerPlayer player, UUID companionId, boolean screenRange) {
        MinecraftServer server = player.level().getServer();
        CompanionEntity companion = server == null ? null : find(server, companionId);
        Optional<String> denial = ActionGate.check(companion != null, companion != null && companion.isOwnedBy(player),
                companion != null && companion.isInert(), screenRange, companion != null && near(player, companion));
        if (denial.isPresent()) {
            boolean mine = companion != null && companion.isOwnedBy(player);
            reply(player, mine ? companion : null, Payloads.Feedback.deny(denial.get()));
            return null;
        }
        return companion;
    }

    /** Snapshot if the player has this companion's screen open, else (for a deny) one action-bar sentence. */
    public static void reply(ServerPlayer player, @Nullable CompanionEntity companion, Payloads.Feedback feedback) {
        if (companion != null && player.containerMenu instanceof CompanionMenu menu
                && companion.companionId() != null && companion.companionId().equals(menu.companionId())) {
            Services.NETWORK.sendToPlayer(player, snapshot(companion, player, feedback));
            return;
        }
        if (!feedback.ok() && !feedback.key().isEmpty()) {
            player.sendSystemMessage(Feedbacks.component(feedback), true);
        }
    }

    // =================================================================================================
    // Handlers
    // =================================================================================================

    public static void setMode(Payloads.SetMode p, ServerPlayer player) {
        if (!admit(player, "set_mode")) {
            return;
        }
        CompanionEntity companion = owned(player, p.companionId(), false);
        if (companion != null) {
            reply(player, companion, companion.trySetMode(p.mode(), player, p.comeHere()));
        }
    }

    public static void rename(Payloads.Rename p, ServerPlayer player) {
        if (!admit(player, "rename")) {
            return;
        }
        CompanionEntity companion = owned(player, p.companionId(), true);
        MinecraftServer server = player.level().getServer();
        if (companion == null || server == null) {
            return;
        }
        String cleaned = RenameRules.clean(p.name());
        CompletableFuture<Boolean> filtered = cleaned.isEmpty() ? CompletableFuture.completedFuture(false)
                : player.getTextFilter().processStreamMessage(cleaned).thenApply(FilteredText::isFiltered);
        filtered.exceptionally(failure -> true).thenAcceptAsync(isFiltered -> {
            if (companion.isRemoved() || !companion.isOwnedBy(player)) {
                return;
            }
            RenameRules.Result result = RenameRules.decide(companion.companionName(), cleaned, text -> isFiltered);
            if (result.outcome() == RenameRules.Outcome.RENAMED || result.outcome() == RenameRules.Outcome.CLEARED) {
                companion.setCompanionName(result.name());
            }
            boolean ok = result.outcome() == RenameRules.Outcome.RENAMED
                    || result.outcome() == RenameRules.Outcome.CLEARED;
            reply(player, companion, new Payloads.Feedback(ok, result.outcome().key(),
                    result.name().map(List::of).orElse(List.of())));
        }, server);
    }

    public static void chipEdit(Payloads.ChipEdit p, ServerPlayer player) {
        if (!admit(player, "chip_edit")) {
            return;
        }
        CompanionEntity companion = owned(player, p.companionId(), true);
        if (companion == null) {
            return;
        }
        if (p.insert()) {
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof ChipItem chip)) {
                reply(player, companion, Payloads.Feedback.deny("gui.nerocompanion.reason.empty_hand"));
                return;
            }
            Component name = held.getHoverName();
            ChipValidation.Result result = companion.insertChip(chip, p.slot());
            if (result.ok()) {
                held.shrink(1);
                reply(player, companion, Payloads.Feedback.ok("gui.nerocompanion.chip.installed", name.getString()));
            } else {
                reply(player, companion, feedback(companion, result));
            }
            return;
        }
        ChipStack removed = companion.removeChip(p.slot());
        if (removed == null) {
            reply(player, companion, Payloads.Feedback.deny("gui.nerocompanion.reason.chip_slot_empty"));
            return;
        }
        Item item = ChipKinds.byId(removed.chip()).map(kind -> ModItems.chip(kind, removed.tier())).orElse(null);
        boolean dropped = false;
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            if (!player.getInventory().add(stack)) {
                // Full inventory: placeItemBackInInventory drops what does not fit at the player's feet.
                //? if >=26.3 {
                /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
                *///?} else {
                player.getInventory().placeItemBackInInventory(stack);
                //?}
                dropped = true;
            }
        }
        reply(player, companion, dropped ? Payloads.Feedback.ok("gui.nerocompanion.chip.dropped")
                : Payloads.Feedback.ok("gui.nerocompanion.chip.removed"));
    }

    public static void recall(Payloads.Recall p, ServerPlayer player) {
        if (!admit(player, "recall")) {
            return;
        }
        CompanionEntity companion = owned(player, p.companionId(), false);
        if (companion == null) {
            return;
        }
        boolean screen = player.containerMenu instanceof CompanionMenu;
        companion.recall(player, null);
        if (screen) {
            player.closeContainer();
        }
        player.sendSystemMessage(Component.translatable("gui.nerocompanion.recalled"), true);
    }

    public static void parkUnpark(Payloads.ParkUnpark p, ServerPlayer player) {
        if (!admit(player, "park_unpark")) {
            return;
        }
        CompanionBayBlockEntity bay = bayInReach(player, p.bay());
        if (bay == null) {
            return;
        }
        Payloads.Feedback feedback;
        if (p.park()) {
            MinecraftServer server = player.level().getServer();
            CompanionEntity companion = server == null ? null : find(server, p.companionId());
            if (companion == null || !companion.isOwnedBy(player)) {
                feedback = Payloads.Feedback.deny(companion == null ? "gui.nerocompanion.reason.not_live"
                        : "gui.nerocompanion.reason.not_owner");
            } else if (companion.level() != player.level()) {
                feedback = Payloads.Feedback.deny("gui.nerocompanion.reason.bay_far");
            } else {
                feedback = bay.park(player, companion);
            }
        } else {
            feedback = bay.unpark(player, p.companionId());
        }
        bay.sendSnapshot(player, feedback);
    }

    public static void reclaim(Payloads.Reclaim p, ServerPlayer player) {
        if (!admit(player, "reclaim")) {
            return;
        }
        CompanionBayBlockEntity bay = bayInReach(player, p.bay());
        if (bay != null) {
            // LOST -> CAPSULE through the ledger (owner only; a second reclaim finds CAPSULE and is refused).
            bay.sendSnapshot(player, za.co.neroland.nerocompanion.recovery.Recovery.reclaim(player, p.companionId()));
        }
    }

    public static void applySkin(Payloads.ApplySkin p, ServerPlayer player) {
        if (!admit(player, "apply_skin")) {
            return;
        }
        CompanionEntity companion = owned(player, p.companionId(), true);
        if (companion != null) {
            // Owner and range are checked by owned(..); the look itself (exists, type, unlocked) by LookService.
            reply(player, companion, za.co.neroland.nerocompanion.look.LookService.apply(player, companion, p.hat(),
                    p.id()));
        }
    }

    public static void openCompanion(Payloads.OpenCompanion p, ServerPlayer player) {
        if (!admit(player, "open_companion")) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        CompanionEntity companion = find(server, p.companionId());
        // Not loaded: the ledger knows whose it is (LIVE, unloaded).
        Optional<UUID> owner = za.co.neroland.nerocompanion.recovery.Recovery.book(server).get(p.companionId())
                .filter(entry -> entry.is(za.co.neroland.nerocompanion.recovery.LedgerEntry.State.LIVE))
                .map(za.co.neroland.nerocompanion.recovery.LedgerEntry::owner);
        boolean mine = companion != null ? companion.isOwnedBy(player)
                : owner.isPresent() && owner.get().equals(player.getUUID());
        if (!mine) {
            // One answer for "not yours" and "no such companion": the reply never confirms that an id exists.
            player.sendSystemMessage(Component.translatable("gui.nerocompanion.reason.not_owner"), true);
            return;
        }
        if (MenuOpener.open(player, CompanionMenu.provider(p.companionId())).isEmpty()) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, companion != null
                ? snapshot(companion, player, Payloads.Feedback.NONE)
                : awaySnapshot(server, player, p.companionId()));
    }

    @Nullable
    private static CompanionBayBlockEntity bayInReach(ServerPlayer player, BlockPos pos) {
        double reach = CompanionBayBlockEntity.REACH;
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > reach * reach
                || !player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof CompanionBayBlockEntity bay)) {
            player.sendSystemMessage(Component.translatable("gui.nerocompanion.reason.bay_far"), true);
            return null;
        }
        return bay;
    }

    // =================================================================================================
    // Reasons and snapshots
    // =================================================================================================

    /** A chip validation failure as a screen / action-bar feedback. */
    public static Payloads.Feedback feedback(CompanionEntity companion, ChipValidation.Result result) {
        return switch (result.outcome()) {
            case WRONG_CATEGORY -> Payloads.Feedback.deny(result.outcome().reasonKey(),
                    "@" + categoriesKey(companion.chassis().categories()));
            case EXCLUSIVE -> Payloads.Feedback.deny(result.outcome().reasonKey(),
                    "@chip.nerocompanion.kind." + result.clash().map(id -> id.getPath()).orElse("unknown"));
            default -> Payloads.Feedback.deny(result.outcome().reasonKey());
        };
    }

    /** The same, as a component (world right-click with a chip). */
    public static Component reason(CompanionEntity companion, ChipValidation.Result result) {
        return Feedbacks.component(feedback(companion, result));
    }

    /** {@code chip.nerocompanion.categories.<sorted ids joined by _>}. */
    public static String categoriesKey(Set<ChipCategory> categories) {
        List<String> ids = new ArrayList<>();
        for (ChipCategory category : ChipCategory.values()) {
            if (categories.contains(category)) {
                ids.add(category.id());
            }
        }
        return "chip.nerocompanion.categories." + (ids.isEmpty() ? "none" : String.join("_", ids));
    }

    /** The full owner-only view of a loaded companion. */
    public static Payloads.CompanionSnapshot snapshot(CompanionEntity c, ServerPlayer viewer,
            Payloads.Feedback feedback) {
        ChipValidation.Chassis chassis = c.chassis();
        List<Payloads.ModeState> modes = new ArrayList<>();
        for (CompanionMode mode : CompanionMode.values()) {
            int state = Payloads.ModeState.SUPPORTED;
            Optional<net.minecraft.resources.Identifier> chip = Optional.empty();
            if (!c.supportsMode(mode)) {
                Optional<ChipKind> unlocker = ChipEffects.unlockerOf(mode);
                if (unlocker.isPresent() && chassis.categories().contains(unlocker.get().category())) {
                    state = Payloads.ModeState.NEEDS_CHIP;
                    chip = Optional.of(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            NeroCompanionCommon.MOD_ID, unlocker.get().itemName(1)));
                } else {
                    state = Payloads.ModeState.TYPE_CANT;
                }
            }
            modes.add(new Payloads.ModeState(mode, state, chip));
        }
        int mask = 0;
        for (ChipCategory category : chassis.categories()) {
            mask |= 1 << category.ordinal();
        }
        List<Payloads.ChipView> chips = new ArrayList<>();
        for (ChipStack chip : c.chips()) {
            chips.add(new Payloads.ChipView(chip.chip(), chip.tier()));
        }
        int flags = Payloads.CompanionSnapshot.LOADED;
        if (c.energy().isLow(NeroCompanionConfig.limits().lowBatteryPercent())) {
            flags |= Payloads.CompanionSnapshot.LOW_BATTERY;
        }
        if (c.effectiveMode() == CompanionMode.SLEEP || c.energy().isEmpty()) {
            flags |= Payloads.CompanionSnapshot.SLEEPING;
        }
        if (!near(viewer, c)) {
            flags |= Payloads.CompanionSnapshot.AWAY;
        }
        if (c.isCharging()) {
            flags |= Payloads.CompanionSnapshot.CHARGING;
        }
        long[] drains = c.drains();
        int points = c.bondPoints();
        return new Payloads.CompanionSnapshot(c.companionId(), Optional.of(c.typeId()), c.companionName().orElse(""),
                c.trait(), c.mode(), modes, c.energy().stored(), c.energy().capacity(), drains[0], drains[1],
                c.chargingFePerTick(), Bond.level(points), Bond.pointsToNext(points), chassis.slots(), mask, chips,
                c.skin(), c.hat(),
                za.co.neroland.nerocompanion.look.LookService.unlocked(viewer, c,
                        za.co.neroland.nerocompanion.look.LookKind.SKIN),
                za.co.neroland.nerocompanion.look.LookService.unlocked(viewer, c,
                        za.co.neroland.nerocompanion.look.LookKind.HAT),
                flags, feedback);
    }

    /** What the screen can show for an owned companion whose chunk is not loaded (HUD route): AWAY. */
    private static Payloads.CompanionSnapshot awaySnapshot(MinecraftServer server, ServerPlayer viewer, UUID id) {
        Payloads.HudEntry entry = HudBoard.forServer(server).entriesFor(viewer.getUUID()).stream()
                .filter(e -> e.companionId().equals(id)).findFirst().orElse(null);
        List<Payloads.ModeState> modes = new ArrayList<>();
        for (CompanionMode mode : EnumSet.allOf(CompanionMode.class)) {
            modes.add(new Payloads.ModeState(mode, Payloads.ModeState.SUPPORTED, Optional.empty()));
        }
        return new Payloads.CompanionSnapshot(id, entry == null ? Optional.empty() : entry.type(),
                entry == null ? "" : entry.name(), 0, entry == null ? CompanionMode.SLEEP : entry.mode(), modes,
                entry == null ? 0L : entry.energyPct(), 100L, 0L, 0L, 0L, entry == null ? 0 : entry.bondLevel(), -1,
                0, 0, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                Payloads.CompanionSnapshot.AWAY, Payloads.Feedback.NONE);
    }
}
