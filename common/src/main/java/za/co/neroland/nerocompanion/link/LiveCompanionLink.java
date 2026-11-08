package za.co.neroland.nerocompanion.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.charge.ChargingPadBlockEntity;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.type.CompanionTypes;
import za.co.neroland.nerocompanion.network.Feedbacks;
import za.co.neroland.nerocompanion.network.RenameRules;
import za.co.neroland.nerocompanion.network.ServerActions;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.progression.CompanionProgress;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.salvage.Salvage;
import za.co.neroland.nerocompanion.salvage.SalvageHold;

/**
 * The link module over the running server: the {@link CompanionLinkView} the sections read and the
 * {@link CompanionLinkActions.Ops} the action runs. Server thread (NeroLink marshals every call onto it). Ownership
 * is always the stored owner UUID - of the ledger entry and of the loaded entity - never proximity.
 */
final class LiveCompanionLink implements CompanionLinkView, CompanionLinkActions.Ops {

    private final MinecraftServer server;

    LiveCompanionLink(MinecraftServer server) {
        this.server = server;
    }

    // =================================================================================================
    // View
    // =================================================================================================

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public List<LedgerEntry> entriesOf(UUID owner) {
        return Recovery.book(this.server).entriesOf(owner);
    }

    @Override
    public Optional<Live> live(LedgerEntry entry) {
        CompanionEntity companion = this.loaded(entry.owner(), entry.companionId());
        if (companion == null) {
            return Optional.empty();
        }
        List<CompanionMode> modes = new ArrayList<>();
        for (CompanionMode mode : CompanionMode.values()) {
            if (companion.supportsMode(mode)) {
                modes.add(mode); // chassis modes plus those an installed chip unlocks
            }
        }
        return Optional.of(new Live(companion.mode(), companion.energy().percent(), Recovery.at(companion),
                companion.companionName(), companion.bondPoints(), companion.skin(), companion.hat(),
                companion.home(), modes));
    }

    @Override
    public Optional<HomePos> ownerAt(UUID owner) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
        return player == null ? Optional.empty() : Optional.of(HomePos.of(player.level(), player.blockPosition()));
    }

    @Override
    public int chargePercent(CompanionRecord record) {
        return CompanionBayBlockEntity.percent(record.energyFe(),
                CompanionBayBlockEntity.capacityOf(this.server, record));
    }

    @Override
    public List<CompanionMode> supportedModes(Identifier type, CompanionMode current) {
        List<CompanionMode> out = new ArrayList<>();
        CompanionTypes.get(this.server, type).ifPresentOrElse(def -> {
            for (CompanionMode mode : CompanionMode.values()) {
                if (def.supports(mode)) {
                    out.add(mode);
                }
            }
        }, () -> {
            out.add(current);
            if (current != CompanionMode.SLEEP) {
                out.add(CompanionMode.SLEEP);
            }
        });
        return out;
    }

    /** A loaded home answers by its block; an unloaded one reads as a Bay (homes are set by parking in one). */
    @Override
    public HomeKind homeKind(HomePos home) {
        for (ServerLevel level : this.server.getAllLevels()) {
            if (!home.isIn(level)) {
                continue;
            }
            if (!level.isLoaded(home.pos())) {
                return HomeKind.BAY;
            }
            BlockEntity block = level.getBlockEntity(home.pos());
            if (block instanceof CompanionBayBlockEntity) {
                return HomeKind.BAY;
            }
            return block instanceof ChargingPadBlockEntity ? HomeKind.PAD : HomeKind.NONE;
        }
        return HomeKind.NONE;
    }

    @Override
    public Optional<SalvageView> salvage(UUID owner) {
        return SalvageHold.book(this.server).get(owner).map(hold -> {
            int count = 0;
            for (ItemStack stack : hold.items()) {
                count += Math.max(0, stack.getCount());
            }
            long ticksLeft = Math.max(0L, hold.expiresAt() - Salvage.now(this.server));
            return new SalvageView(count, System.currentTimeMillis() + ticksLeft * 50L);
        });
    }

    // =================================================================================================
    // Ops
    // =================================================================================================

    @Override
    public Optional<LedgerEntry> entry(UUID companionId) {
        return Recovery.book(this.server).get(companionId);
    }

    @Override
    public boolean online(UUID player) {
        return this.server.getPlayerList().getPlayer(player) != null;
    }

    @Override
    public boolean admit(UUID player) {
        return ServerActions.admitLink(this.server, player);
    }

    @Override
    public CompanionLinkActions.Outcome setMode(UUID owner, UUID companionId, CompanionMode mode) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return CompanionLinkActions.Outcome.refuse("owner_offline");
        }
        CompanionEntity companion = this.loaded(owner, companionId);
        if (companion == null) {
            return CompanionLinkActions.Outcome.refuse("not_loaded");
        }
        Payloads.Feedback feedback = companion.trySetMode(mode, player, false);
        return feedback.ok() ? CompanionLinkActions.Outcome.done()
                : CompanionLinkActions.Outcome.refuse(reason(feedback.key()));
    }

    @Override
    public CompanionLinkActions.Outcome recall(UUID owner, UUID companionId) {
        CompanionEntity companion = this.loaded(owner, companionId);
        if (companion == null) {
            return CompanionLinkActions.Outcome.refuse("not_loaded");
        }
        Optional<HomePos> home = companion.home();
        CompanionBayBlockEntity bay = home.map(h -> Recovery.loadedBay(this.server, h)).orElse(null);
        if (bay == null || !bay.acceptsFromLink()) {
            return CompanionLinkActions.Outcome.refuse("no_bay");
        }
        String refusal = bay.parkFromLink(companion);
        return refusal == null ? CompanionLinkActions.Outcome.done()
                : CompanionLinkActions.Outcome.refuse(reason(refusal));
    }

    @Override
    public CompanionLinkActions.Outcome reclaim(UUID owner, UUID companionId) {
        Optional<LedgerEntry> entry = this.entry(companionId);
        if (entry.isEmpty() || !owner.equals(entry.get().owner())) {
            return CompanionLinkActions.Outcome.refuse("not_owner");
        }
        if (!entry.get().is(State.LOST)) {
            return CompanionLinkActions.Outcome.refuse("not_lost");
        }
        CompanionRecord record = entry.get().snapshot();
        Optional<HomePos> home = record.home();
        CompanionBayBlockEntity bay = home.map(h -> Recovery.loadedBay(this.server, h)).orElse(null);
        if (bay == null || !bay.acceptsFromLink()) {
            return CompanionLinkActions.Outcome.refuse("no_bay");
        }
        long cost = Recovery.reclaimCost();
        if (cost > 0L && !CurrencyApi.withdraw(owner, CoreCurrencies.CREDITS, cost, NeroCompanionCommon.MOD_ID,
                Recovery.COST_REASON)) {
            return CompanionLinkActions.Outcome.refuse("reclaim_cost");
        }
        if (!bay.parkRecordFromLink(record)) {
            if (cost > 0L) {
                CurrencyApi.deposit(owner, CoreCurrencies.CREDITS, cost, NeroCompanionCommon.MOD_ID,
                        Recovery.COST_REASON);
            }
            return CompanionLinkActions.Outcome.refuse("no_bay");
        }
        // LOST -> PARKED in its home Bay (the in-game reclaim's ledger step, ending in the Bay instead of a Capsule).
        Recovery.adoptParked(this.server, record, home.get());
        CompanionProgress.recovered(this.server, owner);
        return CompanionLinkActions.Outcome.done();
    }

    @Override
    public CompanionLinkActions.Outcome rename(UUID owner, UUID companionId, String requestedName) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return CompanionLinkActions.Outcome.refuse("owner_offline");
        }
        CompanionEntity companion = this.loaded(owner, companionId);
        if (companion == null) {
            return CompanionLinkActions.Outcome.refuse("not_loaded");
        }
        String cleaned = RenameRules.clean(requestedName);
        CompletableFuture<Boolean> filtered;
        try {
            filtered = cleaned.isEmpty() ? CompletableFuture.completedFuture(false)
                    : player.getTextFilter().processStreamMessage(cleaned).thenApply(FilteredText::isFiltered);
        } catch (RuntimeException failure) {
            return CompanionLinkActions.Outcome.refuse("filtered");
        }
        filtered = filtered.exceptionally(failure -> true);
        if (filtered.isDone()) {
            return apply(companion, owner, decide(companion, cleaned, filtered.join()));
        }
        // A remote filter answers later: apply on the server thread then, exactly as in-game.
        filtered.thenAcceptAsync(isFiltered -> {
            RenameRules.Result result = decide(companion, cleaned, isFiltered);
            CompanionLinkActions.Outcome outcome = apply(companion, owner, result);
            if (!outcome.ok()) {
                // The action already answered "pending": tell the owner here, as the in-game rename does. The
                // roster keeps the old name. No link event (it would push).
                ServerPlayer now = this.server.getPlayerList().getPlayer(owner);
                if (now != null) {
                    String key = result == null ? "gui.nerocompanion.reason.not_live" : result.outcome().key();
                    now.sendSystemMessage(Feedbacks.component(Payloads.Feedback.deny(key)), true);
                }
            }
        }, this.server);
        return CompanionLinkActions.Outcome.later();
    }

    /** The rename decision, or null when the companion is gone or no longer this owner's. */
    private static RenameRules.Result decide(CompanionEntity companion, String cleaned, boolean isFiltered) {
        if (companion.isRemoved()) {
            return null;
        }
        return RenameRules.decide(companion.companionName(), cleaned, text -> isFiltered);
    }

    private static CompanionLinkActions.Outcome apply(CompanionEntity companion, UUID owner,
            RenameRules.Result result) {
        if (result == null || companion.isRemoved() || !owner.equals(companion.ownerId())) {
            return CompanionLinkActions.Outcome.refuse("not_loaded");
        }
        return switch (result.outcome()) {
            case RENAMED, CLEARED -> {
                companion.setCompanionName(result.name());
                yield CompanionLinkActions.Outcome.done();
            }
            case SAME -> CompanionLinkActions.Outcome.refuse("same_name");
            case FILTERED -> CompanionLinkActions.Outcome.refuse("filtered");
        };
    }

    // =================================================================================================
    // shared
    // =================================================================================================

    /** The loaded, working entity of {@code companionId} if its stored owner is {@code owner}. */
    private CompanionEntity loaded(UUID owner, UUID companionId) {
        CompanionEntity companion = ServerActions.find(this.server, companionId);
        if (companion == null || companion.isInert() || !owner.equals(companion.ownerId())) {
            return null;
        }
        return companion;
    }

    /** {@code gui.nerocompanion.reason.bay_full} → {@code bay_full}. */
    static String reason(String key) {
        if (key == null || key.isBlank()) {
            return "refused";
        }
        int dot = key.lastIndexOf('.');
        return dot >= 0 ? key.substring(dot + 1) : key;
    }
}
