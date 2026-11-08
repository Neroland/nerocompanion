package za.co.neroland.nerocompanion.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.incubation.IncubatorStore;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.salvage.HoldBook;

/**
 * The retention sweep over the real store objects: an owner inactive for {@code privacy.ledgerRetentionDays} loses
 * everything in every store, an active or online owner keeps everything, a failed purge keeps the owner's original
 * last-seen stamp so the next sweep retries (Core 1.17.x semantics), and every store that changed refreshes its backup.
 */
class RetentionSweepTest {

    private static final long DAY = 86_400_000L;
    private static final long RETENTION = 180 * DAY;
    private static final long NOW = 1_900_000_000_000L;
    private static final HomePos HOME = new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(5, 70, 5));

    private final UUID gone = UUID.randomUUID();
    private final UUID active = UUID.randomUUID();
    private final UUID online = UUID.randomUUID();
    private final RecoveryBook ledger = new RecoveryBook();
    private final CapsuleVault vault = new CapsuleVault();
    private final HoldBook<String> hold = new HoldBook<>();
    private final IncubatorStore incubators = new IncubatorStore();
    private final PrivacyPrefs prefs = new PrivacyPrefs();
    private final List<String> backups = new ArrayList<>();
    private int eggs;
    private boolean vaultBroken;

    private static CompanionRecord record(UUID owner) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"), CompanionMode.FOLLOW, 100L, List.of(),
                0, Optional.of("Rusty"), Optional.empty(), Optional.empty(), 1L, Optional.of(HOME), new CompoundTag());
    }

    private void seed(UUID player) {
        // A LIVE companion (written now, so the per-row bound alone would never remove it) and a PARKED one.
        ledger.live(record(player), UUID.randomUUID(), HOME, 0L, NOW);
        ledger.parked(record(player), HOME, 0L, NOW);
        vault.store(record(player));
        hold.add(player, UUID.randomUUID(), "pickaxe", 0L, 100L, NOW);
        incubators.put(new HomePos(HOME.dimension(), new BlockPos(++eggs, 64, 0)), new Incubation(player, 100));
    }

    private RetentionSweep.Store store(String name, java.util.function.Supplier<Set<UUID>> owners,
            java.util.function.Consumer<UUID> purge) {
        return new RetentionSweep.Store() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Set<UUID> owners() {
                return owners.get();
            }

            @Override
            public void purgeOwner(UUID owner) {
                purge.accept(owner);
            }

            @Override
            public void backup() {
                backups.add(name);
            }
        };
    }

    private List<RetentionSweep.Store> stores() {
        return List.of(
                store("recovery_ledger", ledger::owners, ledger::purgeOwner),
                store("capsule_vault", vault::owners, owner -> {
                    if (vaultBroken) {
                        throw new IllegalStateException("disk full");
                    }
                    vault.forgetOwner(owner);
                }),
                store("salvage_hold", hold::owners, hold::forgetOwner),
                store("incubators", incubators::owners, incubators::forgetOwner));
    }

    private RetentionSweep.Activity activity() {
        return RetentionSweep.activity(prefs, () -> backups.add("privacy"));
    }

    private boolean holdsAnything(UUID player) {
        return ledger.holdsFor(player) || vault.holdsFor(player) || hold.holdsFor(player)
                || !incubators.placedBy(player).isEmpty() || prefs.holdsFor(player);
    }

    @Test
    void anInactiveOwnerLosesEverythingAndTheOthersKeepEverything() {
        seed(gone);
        seed(active);
        seed(online);
        prefs.seen(gone, NOW - RETENTION - DAY);
        prefs.set(gone, PrivacyPrefs.Setting.NAMES, true, NOW - RETENTION - DAY);
        prefs.seen(active, NOW - 10 * DAY);
        prefs.seen(online, NOW - RETENTION - DAY); // long ago, but online right now

        PlayerDataErasure.PurgeResult result = RetentionSweep.run(stores(), activity(), online::equals, NOW,
                RETENTION);

        assertEquals(0, result.failed());
        assertEquals(1, result.purged());
        assertFalse(holdsAnything(gone), "the inactive owner is gone from every store, settings included");
        assertTrue(ledger.holdsFor(active) && vault.holdsFor(active) && hold.holdsFor(active)
                && !incubators.placedBy(active).isEmpty());
        assertTrue(ledger.holdsFor(online) && vault.holdsFor(online));
        assertEquals(Optional.of(NOW), prefs.lastSeen(online), "an online owner is stamped as seen");
        assertTrue(backups.containsAll(List.of("recovery_ledger", "capsule_vault", "salvage_hold", "incubators",
                "privacy")), "every changed store refreshed its backup: " + backups);
        assertTrue(ledger.tombstones().isEmpty(), "retention writes no tombstones");
    }

    @Test
    void anInactiveOwnersLoadedCompanionIsUnboundFirstAndCannotReRegisterThem() {
        seed(gone);
        seed(active);
        prefs.seen(gone, NOW - RETENTION - DAY);
        prefs.seen(active, NOW);
        // Companions sitting in loaded chunks (spawn chunks, a chunk loader): each re-registers its owner on its next
        // evaluation if it is still bound - exactly what made the purge useless before the fix.
        java.util.Map<UUID, UUID> loaded = new java.util.HashMap<>(); // entity -> owner
        loaded.put(UUID.randomUUID(), gone);
        loaded.put(UUID.randomUUID(), active);
        List<UUID> unbound = new ArrayList<>();
        java.util.function.Consumer<UUID> unbind = owner -> {
            unbound.add(owner);
            loaded.values().removeIf(owner::equals); // becomes an ownerless Capsule, no tombstone
        };

        PlayerDataErasure.PurgeResult result = RetentionSweep.run(stores(), activity(), id -> false, unbind, NOW,
                RETENTION);
        // One evaluation later: every still-bound loaded companion adopts itself.
        loaded.values().forEach(owner -> ledger.live(record(owner), UUID.randomUUID(), HOME, 1L, NOW));

        assertEquals(List.of(gone), unbound, "only the inactive owner is unbound");
        assertEquals(1, result.purged());
        assertFalse(holdsAnything(gone), "nothing re-registered the inactive owner");
        assertTrue(ledger.tombstones().isEmpty(), "retention is not erasure: no tombstones");
        PlayerDataErasure.PurgeResult next = RetentionSweep.run(stores(), activity(), id -> false, unbind,
                NOW + 1_200_000L, RETENTION);
        assertEquals(0, next.purged());
        assertFalse(prefs.holdsFor(gone), "no fresh clock was started for them");
    }

    @Test
    void aFailingUnbindKeepsTheStampForARetry() {
        seed(gone);
        long stamp = NOW - RETENTION - DAY;
        prefs.seen(gone, stamp);
        PlayerDataErasure.PurgeResult result = RetentionSweep.run(stores(), activity(), id -> false, owner -> {
            throw new IllegalStateException("level gone");
        }, NOW, RETENTION);
        assertEquals(1, result.failed());
        assertEquals(Optional.of(stamp), prefs.lastSeen(gone));
    }

    @Test
    void dataFromBeforeTheClockStartsTheClockInsteadOfBeingPurged() {
        seed(gone);
        RetentionSweep.run(stores(), activity(), id -> false, NOW, RETENTION);
        assertTrue(ledger.holdsFor(gone));
        assertEquals(Optional.of(NOW), prefs.lastSeen(gone));

        RetentionSweep.run(stores(), activity(), id -> false, NOW + RETENTION + DAY, RETENTION);
        assertFalse(holdsAnything(gone), "purged once the clock that started has run out");
    }

    @Test
    void aFailedPurgeKeepsTheOriginalStampAndTheNextSweepRetries() {
        seed(gone);
        long stamp = NOW - RETENTION - DAY;
        prefs.seen(gone, stamp);
        vaultBroken = true;

        PlayerDataErasure.PurgeResult first = RetentionSweep.run(stores(), activity(), id -> false, NOW, RETENTION);

        assertEquals(1, first.failed());
        assertEquals(0, first.purged());
        assertEquals(Optional.of(stamp), prefs.lastSeen(gone), "the original stamp is kept for the retry");
        assertTrue(vault.holdsFor(gone), "the failing store still holds the rows");
        assertFalse(ledger.holdsFor(gone), "every other store was still purged");

        vaultBroken = false;
        PlayerDataErasure.PurgeResult retry = RetentionSweep.run(stores(), activity(), id -> false, NOW + 1_000L,
                RETENTION);
        assertEquals(0, retry.failed());
        assertEquals(1, retry.purged());
        assertFalse(holdsAnything(gone));
    }

    @Test
    void perRowBoundsStillRunForActiveOwnersAndTheirBackupsAreRefreshed() {
        prefs.seen(active, NOW);
        ledger.toCapsule(record(active), 0L, NOW - RETENTION - DAY);
        RetentionSweep.Store ledgerStore = new RetentionSweep.Store() {
            @Override
            public String name() {
                return "recovery_ledger";
            }

            @Override
            public Set<UUID> owners() {
                return ledger.owners();
            }

            @Override
            public void purgeOwner(UUID owner) {
                ledger.purgeOwner(owner);
            }

            @Override
            public int purgeUntouched(long nowMillis, long retentionMillis) {
                return ledger.purgeUntouched(nowMillis, retentionMillis);
            }

            @Override
            public void backup() {
                backups.add("recovery_ledger");
            }
        };
        PlayerDataErasure.PurgeResult result = RetentionSweep.run(List.of(ledgerStore), activity(), id -> false, NOW,
                RETENTION);
        assertEquals(1, result.purged());
        assertFalse(ledger.holdsFor(active), "the unused Capsule entry went");
        assertTrue(prefs.holdsFor(active), "the owner is active: their settings stay");
        assertTrue(backups.contains("recovery_ledger"));
    }
}
