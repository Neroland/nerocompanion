package za.co.neroland.nerocompanion.privacy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.compat.creatures.CreaturePets;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.data.RecoveryLedger;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.incubation.IncubatorStore;
import za.co.neroland.nerocompanion.incubation.Incubators;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.registry.ModDataComponents;
import za.co.neroland.nerocompanion.salvage.Salvage;
import za.co.neroland.nerocompanion.salvage.SalvageHold;

/**
 * The server side of NeroCompanion's privacy controls (POPIA / GDPR):
 * <ul>
 *   <li><b>Opt-outs</b> - {@code privacy.allowNames} (server) and each player's own names / chatter / push settings
 *       ({@code /nscomp privacy ...}), read by the companion's nameplate, the chatter service and (Stage 8) push.</li>
 *   <li><b>Export</b> - {@code /nscomp export}, one JSON file of the caller's own data, rate-limited.</li>
 *   <li><b>Retention</b> - the stores {@link RetentionSweep} runs over, and the activity clock it reads.</li>
 *   <li><b>Capsule names after erasure</b> - a Capsule of an erased owner's companion found in an online player's
 *       inventory or ender chest loses its name (and any owner) the next time the sweep sees it.</li>
 * </ul>
 */
public final class CompanionPrivacy {

    /** One export per player per {@code privacy.exportCooldownSeconds}; memory only. */
    public static final ExportCooldown COOLDOWN = new ExportCooldown();

    /** How often (ticks) online inventories are checked for Capsules of erased owners' companions. */
    public static final long CAPSULE_NAME_SWEEP_TICKS = 200L;

    private CompanionPrivacy() {
    }

    public static PrivacyPrefs prefs(MinecraftServer server) {
        return PrivacyPrefs.get(server);
    }

    // =================================================================================================
    // Opt-outs
    // =================================================================================================

    /** Whether {@code owner}'s companion names may be shown to other players (server switch and their own choice). */
    public static boolean namesShown(@Nullable MinecraftServer server, @Nullable UUID owner) {
        return namesShown(NeroCompanionConfig.allowNames(), server == null || owner == null ? null : prefs(server),
                owner);
    }

    /** Pure form of {@link #namesShown(MinecraftServer, UUID)}. */
    public static boolean namesShown(boolean serverAllows, @Nullable PrivacyPrefs prefs, @Nullable UUID owner) {
        if (!serverAllows) {
            return false;
        }
        return prefs == null || owner == null || !prefs.isOff(owner, PrivacyPrefs.Setting.NAMES);
    }

    /**
     * Whether a companion's name starts hidden when its record is applied (chunk load, deploy, Bay unpark), before its
     * first evaluation. Off the server thread the privacy store is not read and the name stays hidden until the
     * evaluation (at most a second) decides; on the client nothing is hidden here (the server's data decides).
     */
    public static boolean hideOnApply(boolean serverLevel, boolean serverThread,
            java.util.function.BooleanSupplier namesShown) {
        if (!serverLevel) {
            return false;
        }
        if (!serverThread) {
            return true;
        }
        try {
            return !namesShown.getAsBoolean();
        } catch (RuntimeException failure) {
            return true; // when in doubt, hidden
        }
    }

    /** Whether {@code player} wants chatter (their companions' lines, and seeing anyone's). */
    public static boolean chatterAllowed(@Nullable MinecraftServer server, @Nullable UUID player) {
        return server == null || player == null || !prefs(server).isOff(player, PrivacyPrefs.Setting.CHATTER);
    }

    /**
     * Stage 8 seam: whether push notifications about {@code owner}'s companions may be sent. The NeroLink push path
     * must check this (and the category, off by default) before anything leaves the server.
     */
    public static boolean pushAllowed(@Nullable MinecraftServer server, @Nullable UUID owner) {
        return server != null && owner != null && !prefs(server).isOff(owner, PrivacyPrefs.Setting.PUSH);
    }

    /** {@code /nscomp privacy <setting> on|off}. Live companions pick a names change up within a second. */
    public static void set(MinecraftServer server, UUID player, PrivacyPrefs.Setting setting, boolean off) {
        PrivacyPrefs prefs = prefs(server);
        prefs.set(player, setting, off, System.currentTimeMillis());
    }

    /** A player joined: their activity clock restarts, if NeroCompanion holds anything about them. */
    public static void playerJoined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        try {
            UUID id = player.getUUID();
            PrivacyPrefs prefs = prefs(server);
            if (prefs.holdsFor(id) || holdsAnything(server, id)) {
                prefs.seen(id, System.currentTimeMillis());
            }
        } catch (RuntimeException failure) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] the privacy activity clock could not be updated ({})",
                    failure.getClass().getName());
        }
    }

    private static boolean holdsAnything(MinecraftServer server, UUID id) {
        return RecoveryLedger.book(server).holdsFor(id) || CapsuleVault.get(server).holdsFor(id)
                || SalvageHold.book(server).holdsFor(id) || !IncubatorStore.get(server).placedBy(id).isEmpty();
    }

    // =================================================================================================
    // Export
    // =================================================================================================

    /** What an export request answered. */
    public sealed interface ExportResult {
        /** Written: the path relative to the world folder. */
        record Written(String relativePath) implements ExportResult {
        }

        /** Refused by the cooldown: seconds left. */
        record Cooldown(long secondsLeft) implements ExportResult {
        }

        /** The file could not be written. */
        record Failed() implements ExportResult {
        }
    }

    public static CompanionExport.Settings exportSettings() {
        return new CompanionExport.Settings(NeroCompanionConfig.allowNames(), NeroCompanionConfig.ledgerRetentionDays(),
                NeroCompanionConfig.lostAfterOfflineDays());
    }

    /** One held item, as the export shows it: item id and count. */
    public static JsonElement describeItem(Object item) {
        JsonObject out = new JsonObject();
        if (item instanceof ItemStack stack) {
            out.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            out.addProperty("count", stack.getCount());
        } else {
            out.addProperty("item", String.valueOf(item));
        }
        return out;
    }

    /** {@code /nscomp export}: builds and writes {@code subject}'s export. Server thread. */
    public static ExportResult export(MinecraftServer server, UUID subject) {
        long now = System.currentTimeMillis();
        long left = COOLDOWN.tryAcquire(subject, now, NeroCompanionConfig.exportCooldownMillis());
        if (left > 0) {
            return new ExportResult.Cooldown(left);
        }
        try {
            CompanionExport.Sources sources = new CompanionExport.Sources(RecoveryLedger.book(server),
                    CapsuleVault.get(server), SalvageHold.book(server), IncubatorStore.get(server),
                    CreaturePets.petsOf(subject), prefs(server));
            JsonObject document = CompanionExport.build(subject, sources, exportSettings(),
                    CompanionPrivacy::describeItem, now);
            Path directory = CompanionExport.directory(server);
            if (directory == null) {
                throw new IOException("no export folder");
            }
            return new ExportResult.Written(CompanionExport.write(directory, subject, document));
        } catch (IOException | RuntimeException failure) {
            COOLDOWN.refund(subject);
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a privacy export could not be written ({}).",
                    failure.getClass().getName());
            return new ExportResult.Failed();
        }
    }

    // =================================================================================================
    // Erasure stores added in Stage 7
    // =================================================================================================

    /** The erasure stores this class owns: the privacy settings row and the export file + cooldown. */
    static List<CompanionErasure.Store> erasureStores() {
        List<CompanionErasure.Store> out = new ArrayList<>();
        out.add(new CompanionErasure.Store() {
            @Override
            public String name() {
                return "privacy";
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                if (server != null) {
                    prefs(server).forget(player);
                }
            }

            @Override
            public void backup(@Nullable MinecraftServer server) {
                if (server != null) {
                    SavedDataRecovery.backupNow(server.overworld(), PrivacyPrefs.TYPE, prefs(server),
                            PrivacyPrefs.RECOVERY_NAME);
                }
            }
        });
        out.add(new CompanionErasure.Store() {
            @Override
            public String name() {
                return "exports";
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                COOLDOWN.forget(player);
                CompanionExport.delete(CompanionExport.directory(server), player);
            }
        });
        return out;
    }

    // =================================================================================================
    // Retention
    // =================================================================================================

    /** Every store the retention sweep reaches on {@code server}. */
    public static List<RetentionSweep.Store> retentionStores(MinecraftServer server) {
        List<RetentionSweep.Store> stores = new ArrayList<>();
        stores.add(new RetentionSweep.Store() {
            @Override
            public String name() {
                return "recovery_ledger";
            }

            @Override
            public Set<UUID> owners() {
                return RecoveryLedger.book(server).owners();
            }

            @Override
            public void purgeOwner(UUID owner) {
                RecoveryLedger.book(server).purgeOwner(owner);
            }

            @Override
            public int purgeUntouched(long nowMillis, long retentionMillis) {
                return RecoveryLedger.book(server).purgeUntouched(nowMillis, retentionMillis);
            }

            @Override
            public void backup() {
                SavedDataRecovery.backupNow(server.overworld(), RecoveryLedger.TYPE, RecoveryLedger.get(server),
                        RecoveryLedger.RECOVERY_NAME);
            }
        });
        stores.add(new RetentionSweep.Store() {
            @Override
            public String name() {
                return "capsule_vault";
            }

            @Override
            public Set<UUID> owners() {
                return CapsuleVault.get(server).owners();
            }

            @Override
            public void purgeOwner(UUID owner) {
                CapsuleVault.get(server).forgetOwner(owner);
            }

            @Override
            public int purgeUntouched(long nowMillis, long retentionMillis) {
                return CapsuleVault.get(server).purgeOlderThan(nowMillis, retentionMillis);
            }

            @Override
            public void backup() {
                SavedDataRecovery.backupNow(server.overworld(), CapsuleVault.TYPE, CapsuleVault.get(server),
                        CapsuleVault.RECOVERY_NAME);
            }
        });
        stores.add(new RetentionSweep.Store() {
            @Override
            public String name() {
                return "salvage_hold";
            }

            @Override
            public Set<UUID> owners() {
                return SalvageHold.book(server).owners();
            }

            @Override
            public void purgeOwner(UUID owner) {
                Salvage.forgetOwner(owner);
                SalvageHold.book(server).forgetOwner(owner);
            }

            @Override
            public int purgeUntouched(long nowMillis, long retentionMillis) {
                return SalvageHold.book(server).purgeUntouched(nowMillis, retentionMillis);
            }

            @Override
            public void backup() {
                SavedDataRecovery.backupNow(server.overworld(), SalvageHold.TYPE, SalvageHold.get(server),
                        SalvageHold.RECOVERY_NAME);
            }
        });
        stores.add(new RetentionSweep.Store() {
            @Override
            public String name() {
                return "incubators";
            }

            @Override
            public Set<UUID> owners() {
                return IncubatorStore.get(server).owners();
            }

            @Override
            public void purgeOwner(UUID owner) {
                Incubators.forgetOwner(server, owner);
            }

            @Override
            public void backup() {
                SavedDataRecovery.backupNow(server.overworld(), IncubatorStore.TYPE, IncubatorStore.get(server),
                        IncubatorStore.RECOVERY_NAME);
            }
        });
        stores.add(new RetentionSweep.Store() {
            @Override
            public String name() {
                return "memory";
            }

            @Override
            public Set<UUID> owners() {
                return Set.of();
            }

            @Override
            public void purgeOwner(UUID owner) {
                CreaturePets.forgetOwner(owner);
                COOLDOWN.forget(owner);
            }
        });
        stores.add(new RetentionSweep.Store() {
            @Override
            public String name() {
                return "exports";
            }

            @Override
            public Set<UUID> owners() {
                return Set.of();
            }

            @Override
            public void purgeOwner(UUID owner) {
                CompanionExport.delete(CompanionExport.directory(server), owner);
            }

            @Override
            public int purgeUntouched(long nowMillis, long retentionMillis) {
                return CompanionExport.purgeExpired(CompanionExport.directory(server), nowMillis);
            }
        });
        return stores;
    }

    /** One retention sweep on {@code server}. */
    public static PlayerDataErasure.PurgeResult sweep(MinecraftServer server) {
        PrivacyPrefs prefs = prefs(server);
        RetentionSweep.Activity activity = RetentionSweep.activity(prefs, () -> SavedDataRecovery.backupNow(
                server.overworld(), PrivacyPrefs.TYPE, prefs, PrivacyPrefs.RECOVERY_NAME));
        Predicate<UUID> online = id -> server.getPlayerList().getPlayer(id) != null;
        return RetentionSweep.run(retentionStores(server), activity, online, owner -> CompanionErasure.unbindLoaded(
                server, owner), System.currentTimeMillis(), NeroCompanionConfig.ledgerRetentionMillis());
    }

    // =================================================================================================
    // Capsule names after erasure
    // =================================================================================================

    /**
     * {@code record} as it must be on a Capsule once its owner was erased (the ledger holds a tombstone for it): no
     * name, no owner, bond reset - {@link za.co.neroland.nerocompanion.recovery.ReclaimRecords#erased}. Empty when
     * nothing needs to change.
     */
    public static Optional<CompanionRecord> scrubbedCapsule(CompanionRecord record, Predicate<UUID> tombstoned) {
        if (!tombstoned.test(record.companionId())) {
            return Optional.empty();
        }
        boolean personal = record.name().isPresent() || record.bondPoints() != 0
                || !CompanionRecord.NO_OWNER.equals(record.ownerId());
        return personal ? Optional.of(za.co.neroland.nerocompanion.recovery.ReclaimRecords.erased(record)
                .forNetwork()) : Optional.empty();
    }

    /** Every {@link #CAPSULE_NAME_SWEEP_TICKS}: online inventories and ender chests. @return Capsules scrubbed */
    public static int sweepCapsuleNames(MinecraftServer server) {
        RecoveryBook book = RecoveryLedger.book(server);
        if (!book.hasTombstones()) {
            return 0;
        }
        int scrubbed = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scrubbed += scrub(player.getInventory(), book);
            scrubbed += scrub(player.getEnderChestInventory(), book);
        }
        return scrubbed;
    }

    private static int scrub(Container container, RecoveryBook book) {
        int scrubbed = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompanionRecord record = stack.get(ModDataComponents.COMPANION.get());
            if (record == null) {
                continue;
            }
            Optional<CompanionRecord> clean = scrubbedCapsule(record, book::isTombstoned);
            if (clean.isPresent()) {
                stack.set(ModDataComponents.COMPANION.get(), clean.get());
                scrubbed++;
            }
        }
        return scrubbed;
    }
}
