package za.co.neroland.nerocompanion.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.alert.AlertText;
import za.co.neroland.nerocompanion.alert.CompanionAlerts;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.data.RecoveryLedger;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.item.CapsuleItem;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.registry.ModItems;

/**
 * The Recovery Ledger in the game: the calls the entity, the deployer, the Capsule, the Bay, the screens and the
 * command make, each a thin wrapper over {@link RecoveryBook} with the server's clock (overworld game time) and the
 * alerts. Server thread only.
 */
public final class Recovery {

    /** Lost sweep cadence (ticks). */
    public static final long SWEEP_TICKS = 200L;
    /** Retention sweep cadence (ticks): every 20 minutes of server time, and once shortly after start. */
    public static final long RETENTION_TICKS = 24_000L;
    /** After a partly failed retention sweep the next one runs this much sooner (one minute). */
    public static final long RETENTION_RETRY_TICKS = 1_200L;
    private static long retentionInterval = RETENTION_TICKS;

    /** Reason a reclaim charges for, in Core's currency attribution. */
    public static final String COST_REASON = "companion_reclaim";

    /** How a companion is being brought out (decides the ledger check). */
    public sealed interface Source {
        /** Brand new (blueprint, egg, hatch, debug spawn). */
        record Fresh() implements Source {
        }

        /** From a Capsule carrying {@code ticket} (0 = a Capsule from before the ledger). */
        record Capsule(long ticket) implements Source {
        }

        /** Unparked from the Bay at {@code bay}. */
        record Parked(HomePos bay) implements Source {
        }

        Source FRESH = new Fresh();
    }

    private static long lastRetention = Long.MIN_VALUE;

    private Recovery() {
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static long millis() {
        return System.currentTimeMillis();
    }

    public static RecoveryBook book(MinecraftServer server) {
        return RecoveryLedger.book(server);
    }

    public static HomePos at(CompanionEntity companion) {
        return HomePos.of(companion.level(), companion.blockPosition());
    }

    // =================================================================================================
    // Deploy
    // =================================================================================================

    /** Why {@code companionId} cannot come out from {@code source} (a lang key), or null. */
    @Nullable
    public static String check(MinecraftServer server, Source source, UUID companionId) {
        RecoveryBook book = book(server);
        if (source instanceof Source.Capsule capsule) {
            return book.checkCapsule(companionId, capsule.ticket());
        }
        if (source instanceof Source.Parked parked) {
            return book.checkParked(companionId, parked.bay());
        }
        return book.checkFresh(companionId);
    }

    /** The companion is in the world: LIVE as its entity. */
    public static void deployed(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level) || companion.companionId() == null
                || companion.ownerId() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        book(server).live(companion.toRecord(), companion.getUUID(), at(companion), now(server), millis());
    }

    /**
     * The caps' view of the ledger now: every LIVE companion of the owner, and per region only those seen within
     * {@code lostAfterMinutes} (companions left in unloaded chunks stop filling a region).
     */
    public static za.co.neroland.nerocompanion.scheduler.LiveCompanionCounter caps(MinecraftServer server) {
        return book(server).counter(now(server), NeroCompanionConfig.lostAfterMinutes() * 60L * 20L);
    }

    // =================================================================================================
    // Leaving the world
    // =================================================================================================

    /**
     * Recall: LIVE as {@code entityId} → CAPSULE.
     *
     * @return the new Capsule's ticket, or 0 when the ledger refuses (this entity is not the companion)
     */
    public static long recalled(MinecraftServer server, CompanionRecord record, UUID entityId) {
        return book(server).recall(record, entityId, now(server), millis());
    }

    /** Park: LIVE as {@code entityId} → PARKED at {@code bay}; false when the ledger refuses. */
    public static boolean parked(MinecraftServer server, CompanionRecord record, UUID entityId, HomePos bay) {
        return book(server).park(record, entityId, bay, now(server), millis());
    }

    /** A Bay record the ledger did not know (reconcile): PARKED here. */
    public static void adoptParked(MinecraftServer server, CompanionRecord record, HomePos bay) {
        book(server).adoptParked(record, bay, now(server), millis());
    }

    /** The Bay at {@code bay} is breaking: what this record becomes. */
    public static RecoveryBook.HandOutResult handOut(MinecraftServer server, CompanionRecord record, HomePos bay) {
        return book(server).handOut(record, bay, now(server), millis());
    }

    /** What the loaded world says about a Bay holding {@code companionId}. */
    public static RecoveryBook.BayProbe bays(MinecraftServer server) {
        return (bay, companionId) -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (!bay.isIn(level)) {
                    continue;
                }
                if (!level.isLoaded(bay.pos())) {
                    return RecoveryBook.BayState.UNKNOWN;
                }
                if (level.getBlockEntity(bay.pos()) instanceof CompanionBayBlockEntity found) {
                    if (found.hasUnreadableData()) {
                        return RecoveryBook.BayState.UNKNOWN;
                    }
                    return found.slots().indexOf(companionId) >= 0 ? RecoveryBook.BayState.HOLDS
                            : RecoveryBook.BayState.MISSING;
                }
                return RecoveryBook.BayState.MISSING;
            }
            return RecoveryBook.BayState.UNKNOWN;
        };
    }

    /** The entity died (or vanished): LIVE → LOST, with the *lost* alert. */
    public static void died(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level) || companion.companionId() == null
                || companion.ownerId() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        CompanionRecord record = companion.toRecord();
        if (book(server).died(record, companion.getUUID(), at(companion), now(server), millis())) {
            CompanionAlerts.raise(server, record.ownerId(), AlertText.Kind.LOST, record.companionId(), record.type(),
                    record.name());
        }
    }

    // =================================================================================================
    // The once-a-second question: who am I?
    // =================================================================================================

    /**
     * Asks the ledger what this loaded entity is and keeps the entry fresh (10-second cadence, or now when
     * {@code force}). The caller acts on STALE (discard) and ERASED (unbind).
     */
    public static RecoveryBook.Verdict evaluate(CompanionEntity companion, boolean force) {
        if (!(companion.level() instanceof ServerLevel level) || companion.companionId() == null) {
            return RecoveryBook.Verdict.OK;
        }
        if (companion.ownerId() == null) {
            // Ownerless (an erased owner's Capsule redeployed is owned again; this is a broken or unbound one).
            return RecoveryBook.Verdict.OK;
        }
        MinecraftServer server = level.getServer();
        RecoveryBook book = book(server);
        RecoveryBook.Verdict verdict = book.verdict(companion.companionId(), companion.getUUID(), companion.ownerId(),
                bays(server));
        long now = now(server);
        switch (verdict) {
            case OK -> {
                if (force || book.touchDue(companion.companionId(), now)) {
                    book.touch(companion.toRecord(), companion.getUUID(), at(companion), now, millis(), true);
                }
            }
            case ADOPT -> {
                book.live(companion.toRecord(), companion.getUUID(), at(companion), now, millis());
            }
            case FOUND -> {
                CompanionRecord record = companion.toRecord();
                book.live(record, companion.getUUID(), at(companion), now, millis());
                CompanionAlerts.raise(server, record.ownerId(), AlertText.Kind.RECOVERED, record.companionId(),
                        record.type(), record.name());
            }
            case STALE, ERASED -> {
            }
        }
        return verdict;
    }

    // =================================================================================================
    // Server tick, login
    // =================================================================================================

    /**
     * Each server tick: every 10 s the lost sweep (unseen while the owner is online, or untouched for
     * {@code lostAfterOfflineDays} whatever the owner does) and the orphaned-Bay sweep; retention every 20 minutes.
     */
    public static void tick(MinecraftServer server) {
        long now = now(server);
        if (now % SWEEP_TICKS == 0L) {
            long lostTicks = NeroCompanionConfig.lostAfterMinutes() * 60L * 20L;
            RecoveryBook book = book(server);
            List<LedgerEntry> lost = new ArrayList<>(book.sweepLost(now, lostTicks,
                    owner -> server.getPlayerList().getPlayer(owner) != null, millis(),
                    NeroCompanionConfig.lostAfterOfflineMillis()));
            lost.addAll(book.sweepOrphanedParked(bays(server), now, millis()));
            for (LedgerEntry entry : lost) {
                CompanionRecord record = entry.snapshot();
                CompanionAlerts.raise(server, entry.owner(), AlertText.Kind.LOST, entry.companionId(), record.type(),
                        record.name());
            }
        }
        if (now % za.co.neroland.nerocompanion.privacy.CompanionPrivacy.CAPSULE_NAME_SWEEP_TICKS == 0L) {
            za.co.neroland.nerocompanion.privacy.CompanionPrivacy.sweepCapsuleNames(server);
        }
        if (lastRetention == Long.MIN_VALUE || now - lastRetention >= retentionInterval || now < lastRetention) {
            lastRetention = now;
            PlayerDataErasure.PurgeResult result = purgeRetention(server);
            // A partly failed sweep keeps what it could not purge and tries again soon (Core 1.17.x semantics).
            retentionInterval = result.failed() > 0 ? RETENTION_RETRY_TICKS : RETENTION_TICKS;
        }
    }

    /**
     * Retention (see {@code privacy.RetentionSweep}): every store of players inactive for
     * {@code privacy.ledgerRetentionDays}, then the per-row bounds (capsuled/lost ledger entries, tombstones, vault
     * entries, salvage holds, export files), then the backups of every store that changed.
     */
    public static PlayerDataErasure.PurgeResult purgeRetention(MinecraftServer server) {
        PlayerDataErasure.PurgeResult result;
        try {
            result = za.co.neroland.nerocompanion.privacy.CompanionPrivacy.sweep(server);
        } catch (RuntimeException | LinkageError failure) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] Retention sweep failed ({}); retrying later",
                    failure.getClass().getName());
            return new PlayerDataErasure.PurgeResult(0, 1);
        }
        if (result.purged() > 0 || result.failed() > 0) {
            NeroCompanionCommon.LOGGER.info("[NeroCompanion] Retention sweep removed {} stale record(s); {} step(s) "
                    + "failed and will be retried", result.purged(), result.failed());
        }
        return result;
    }

    public static void ownerJoined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            book(server).ownerJoined(player.getUUID(), now(server));
        }
    }

    public static void serverStopped() {
        lastRetention = Long.MIN_VALUE;
        retentionInterval = RETENTION_TICKS;
    }

    // =================================================================================================
    // Reclaim
    // =================================================================================================

    /** The configured reclaim cost in credits, or 0 when free (no cost set, or no real currency provider). */
    public static long reclaimCost() {
        return za.co.neroland.nerocompanion.compat.neroeconomy.NeroEconomyCompat.reclaimCost(
                NeroCompanionConfig.reclaimCost());
    }

    /**
     * {@code player} reclaims their LOST companion {@code companionId}: charged (when a cost applies), moved to
     * CAPSULE with a new ticket, and given to them as a Capsule (inventory, else at their feet).
     */
    public static Payloads.Feedback reclaim(ServerPlayer player, UUID companionId) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_live");
        }
        RecoveryBook book = book(server);
        Optional<LedgerEntry> entry = book.get(companionId);
        if (entry.isEmpty() || !entry.get().owner().equals(player.getUUID())) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_owner");
        }
        if (!entry.get().is(LedgerEntry.State.LOST)) {
            return Payloads.Feedback.deny(RecoveryBook.NOT_LOST);
        }
        long cost = reclaimCost();
        if (cost > 0L && !CurrencyApi.withdraw(player.getUUID(), CoreCurrencies.CREDITS, cost,
                NeroCompanionCommon.MOD_ID, COST_REASON)) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.reclaim_cost", String.valueOf(cost));
        }
        RecoveryBook.Reclaim reclaim = book.reclaim(player.getUUID(), companionId, now(server), millis());
        if (!reclaim.ok()) {
            if (cost > 0L) {
                CurrencyApi.deposit(player.getUUID(), CoreCurrencies.CREDITS, cost, NeroCompanionCommon.MOD_ID,
                        COST_REASON);
            }
            return Payloads.Feedback.deny(reclaim.reason());
        }
        CompanionRecord record = reclaim.record();
        ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
        CapsuleItem.store(server, capsule, record, CompanionBayBlockEntity.percent(record.energyFe(),
                CompanionBayBlockEntity.capacityOf(server, record)), reclaim.ticket());
        //? if >=26.3 {
        /*player.getInventory().placeItemBackInInventory(capsule, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.getInventory().placeItemBackInInventory(capsule);
        //?}
        za.co.neroland.nerocompanion.progression.CompanionProgress.recovered(server, player.getUUID());
        return Payloads.Feedback.ok("gui.nerocompanion.bay.reclaimed.ok", displayName(record));
    }

    /** The viewer's LOST companions as Bay rows (type, name, minutes ago, cost - never where). */
    public static List<Payloads.BayListEntry> lostRows(ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        List<Payloads.BayListEntry> rows = new ArrayList<>();
        if (server == null) {
            return rows;
        }
        long now = now(server);
        long cost = reclaimCost();
        for (LedgerEntry entry : book(server).lostOf(viewer.getUUID())) {
            CompanionRecord record = entry.snapshot();
            long minutes = Math.max(0L, (now - entry.lostAtTick()) / 1200L);
            rows.add(new Payloads.BayListEntry(entry.companionId(), Optional.of(record.type()),
                    record.name().orElse(""), CompanionBayBlockEntity.percent(record.energyFe(),
                            CompanionBayBlockEntity.capacityOf(server, record)), minutes,
                    cost > 0L ? String.valueOf(cost) : ""));
        }
        return rows;
    }

    /** Screen / chat name: the companion's name, else its type (as a {@code @} lang reference). */
    public static String displayName(CompanionRecord record) {
        return record.name().orElse("@entity." + record.type().getNamespace() + "." + record.type().getPath());
    }

    /** Operator release: the companion becomes LOST (reclaimable by its owner). */
    public static boolean release(MinecraftServer server, UUID companionId) {
        return book(server).release(companionId, now(server), millis());
    }

    /** Debug clear: the companion is gone on purpose and not reclaimable. */
    public static void forget(MinecraftServer server, UUID companionId) {
        book(server).forget(companionId);
    }

    /** The Bay at {@code bay} if its chunk is loaded on this server. */
    @Nullable
    public static CompanionBayBlockEntity loadedBay(MinecraftServer server, HomePos bay) {
        for (ServerLevel level : server.getAllLevels()) {
            if (bay.isIn(level)) {
                BlockPos pos = bay.pos();
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CompanionBayBlockEntity found) {
                    return found;
                }
            }
        }
        return null;
    }
}
