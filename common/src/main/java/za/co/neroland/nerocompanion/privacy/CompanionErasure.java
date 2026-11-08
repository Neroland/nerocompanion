package za.co.neroland.nerocompanion.privacy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.compat.creatures.CreaturePets;
import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.data.RecoveryLedger;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.hud.HudBoard;
import za.co.neroland.nerocompanion.incubation.IncubatorStore;
import za.co.neroland.nerocompanion.incubation.Incubators;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.salvage.Salvage;
import za.co.neroland.nerocompanion.salvage.SalvageHold;
import za.co.neroland.nerocompanion.scheduler.CompanionIndex;

/**
 * NeroCompanion's one {@code PlayerDataErasure} registration. A request for a player:
 * <ol>
 *   <li>unbinds their loaded companions (ownerless Capsule at home or where they stand) and their companions parked
 *       in loaded Bays - found through the Recovery Ledger before it is cleared;</li>
 *   <li>clears every player-keyed store: Recovery Ledger, Salvage Hold, Capsule Vault, incubator store, privacy
 *       settings (each then {@code SavedDataRecovery.backupNow}'d, so the backup file loses the rows too), the export
 *       file and its cooldown, NeroCreatures pets, death windows, HUD board, the loaded-entity index and the mining
 *       memory.</li>
 * </ol>
 * Every store is attempted even if one fails; a failure is logged with the store name only (never the UUID) and
 * thrown, so Core's {@code PlayerDataErasure.eraseAndReport} reports the request as incomplete and its retention sweep
 * ({@code purgeInactiveReport}) keeps the player's last-seen stamp and retries.
 */
public final class CompanionErasure {

    /** One player-keyed store. */
    public interface Store {
        /** Stable, non-identifying name (logs, conformance probes). */
        String name();

        void erase(@Nullable MinecraftServer server, UUID player);

        /** Refresh the store's recovery backup after erasing (no-op for memory-only stores). */
        default void backup(@Nullable MinecraftServer server) {
        }
    }

    private static final List<Store> STORES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private CompanionErasure() {
    }

    /** Common init step 4: the stores and the one Core registration (idempotent). */
    public static void init() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        STORES.add(new Store() {
            @Override
            public String name() {
                return "recovery_ledger";
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                if (server != null) {
                    RecoveryLedger.book(server).forgetOwner(player);
                }
            }

            @Override
            public void backup(@Nullable MinecraftServer server) {
                if (server != null) {
                    SavedDataRecovery.backupNow(server.overworld(), RecoveryLedger.TYPE, RecoveryLedger.get(server),
                            RecoveryLedger.RECOVERY_NAME);
                }
            }
        });
        STORES.add(new Store() {
            @Override
            public String name() {
                return "salvage_hold";
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                Salvage.forgetOwner(player);
                if (server != null) {
                    SalvageHold.book(server).forgetOwner(player);
                }
            }

            @Override
            public void backup(@Nullable MinecraftServer server) {
                if (server != null) {
                    SavedDataRecovery.backupNow(server.overworld(), SalvageHold.TYPE, SalvageHold.get(server),
                            SalvageHold.RECOVERY_NAME);
                }
            }
        });
        STORES.add(new Store() {
            @Override
            public String name() {
                return "capsule_vault";
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                if (server != null) {
                    CapsuleVault.get(server).forgetOwner(player);
                }
            }

            @Override
            public void backup(@Nullable MinecraftServer server) {
                if (server != null) {
                    SavedDataRecovery.backupNow(server.overworld(), CapsuleVault.TYPE, CapsuleVault.get(server),
                            CapsuleVault.RECOVERY_NAME);
                }
            }
        });
        STORES.add(new Store() {
            @Override
            public String name() {
                return "incubators";
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                if (server != null) {
                    Incubators.forgetOwner(server, player);
                }
            }

            @Override
            public void backup(@Nullable MinecraftServer server) {
                if (server != null) {
                    SavedDataRecovery.backupNow(server.overworld(), IncubatorStore.TYPE, IncubatorStore.get(server),
                            IncubatorStore.RECOVERY_NAME);
                }
            }
        });
        STORES.add(memory("creature_pets", (server, player) -> CreaturePets.forgetOwner(player)));
        STORES.add(memory("session_memory", (server, player) -> {
            za.co.neroland.nerocompanion.mining.BotFocus.forget(player);
            za.co.neroland.nerocompanion.mining.MiningCorners.forget(player);
            za.co.neroland.nerocompanion.link.CompanionLinkEvents.forget(player);
            if (server != null) {
                CompanionIndex.forServer(server).removeOwner(player);
                HudBoard.forServer(server).entriesFor(player).forEach(e -> HudBoard.forServer(server)
                        .remove(e.companionId()));
            }
        }));
        STORES.addAll(CompanionPrivacy.erasureStores());
        PlayerDataErasure.register(CompanionErasure::erase);
    }

    private static Store memory(String name, java.util.function.BiConsumer<MinecraftServer, UUID> erase) {
        return new Store() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void erase(@Nullable MinecraftServer server, UUID player) {
                erase.accept(server, player);
            }
        };
    }

    /** The registered stores (tests and the export in Stage 7). */
    public static List<Store> stores() {
        return List.copyOf(STORES);
    }

    /** The Core eraser: unbind live and parked companions, then clear every store. */
    public static void erase(@Nullable MinecraftServer server, UUID player) {
        if (server != null) {
            unbindLoaded(server, player);
        }
        eraseStores(STORES, server, player);
    }

    /** Erases {@code stores} for {@code player}, each then backed up; every store is attempted. */
    public static void eraseStores(List<Store> stores, @Nullable MinecraftServer server, UUID player) {
        int failures = 0;
        for (Store store : stores) {
            try {
                store.erase(server, player);
                store.backup(server);
            } catch (Throwable failure) {
                failures++;
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] erasing player data failed in store '{}' ({})",
                        store.name(), failure.getClass().getName());
            }
        }
        if (failures > 0) {
            throw new IllegalStateException("NeroCompanion could not erase " + failures + " store(s)");
        }
    }

    /**
     * The loaded half of the unbind (erasure, and the inactive-owner retention sweep): the ledger says where the
     * player's companions are - LIVE ones loaded on this server and PARKED ones in loaded Bays become ownerless
     * Capsules - and the loaded-entity index catches loaded ones the ledger does not list. Unloaded ones unbind
     * themselves when they next load (erasure, via the tombstone) or are adopted again (retention).
     */
    public static void unbindLoaded(MinecraftServer server, UUID player) {
        List<LedgerEntry> entries = new ArrayList<>(RecoveryLedger.book(server).entriesOf(player));
        for (LedgerEntry entry : entries) {
            try {
                if (entry.is(LedgerEntry.State.LIVE) && entry.entity().isPresent()) {
                    unbindEntity(server, entry.entity().get(), player);
                } else if (entry.is(LedgerEntry.State.PARKED) && entry.bay().isPresent()) {
                    CompanionBayBlockEntity bay = za.co.neroland.nerocompanion.recovery.Recovery.loadedBay(server,
                            entry.bay().get());
                    if (bay != null) {
                        bay.unbindOwner(player);
                    }
                }
            } catch (RuntimeException failure) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a companion could not be unbound during erasure ({})",
                        failure.getClass().getName());
            }
        }
        // Loaded companions the ledger does not (yet) list - adopted this second, or between a purge and a re-adopt.
        for (UUID entityId : CompanionIndex.forServer(server).entitiesOf(player)) {
            try {
                unbindEntity(server, entityId, player);
            } catch (RuntimeException failure) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a companion could not be unbound during erasure ({})",
                        failure.getClass().getName());
            }
        }
    }

    private static void unbindEntity(MinecraftServer server, UUID entityId, UUID player) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof CompanionEntity companion && !companion.isRemoved()
                    && player.equals(companion.ownerId())) {
                companion.unbindForErasure();
                return;
            }
        }
    }
}
