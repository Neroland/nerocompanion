package za.co.neroland.nerocompanion.privacy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * The retention sweep (pure; the server wiring is {@link CompanionPrivacy#retentionStores}). One pass:
 * <ol>
 *   <li><b>Inactive owners.</b> Their loaded companions and loaded Bay records become ownerless Capsules first; then
 *       every player who has data in any store and has not been seen for
 *       {@code privacy.ledgerRetentionDays} loses all of it, in every store. A player online now is stamped as seen; a
 *       player with data but no stamp yet (data from before the clock existed) starts the clock now. If any store
 *       fails for that owner, their original last-seen stamp is put back, so the next sweep tries again - the same
 *       rule as Neroland Core 1.17.x's {@code purgeInactiveReport}: a partly failed purge keeps the timestamp.</li>
 *   <li><b>Old rows of active owners.</b> Each store's own per-row bound (capsuled or lost ledger entries,
 *       tombstones, vault entries and salvage holds not used for the retention period; export files after a week).</li>
 *   <li><b>Backups.</b> Every store that changed refreshes its {@code SavedDataRecovery} backup, so purged rows do not
 *       live on in the backup file.</li>
 * </ol>
 * Every store is attempted even if another fails. Failures are logged with the store name and exception class only.
 */
public final class RetentionSweep {

    /** One store the sweep reaches. */
    public interface Store {
        /** Stable, non-identifying name (logs). */
        String name();

        /** Every player this store holds data for. */
        Set<UUID> owners();

        /** Remove everything of an inactive {@code owner} (not an erasure: no tombstones). */
        void purgeOwner(UUID owner);

        /** The store's per-row bound. @return rows removed */
        default int purgeUntouched(long nowMillis, long retentionMillis) {
            return 0;
        }

        /** Refresh the store's recovery backup (no-op for memory-only stores and files). */
        default void backup() {
        }
    }

    /** The activity clock (backed by {@link PrivacyPrefs}). */
    public interface Activity {
        Optional<Long> lastSeen(UUID player);

        void seen(UUID player, long millis);

        void restoreSeen(UUID player, long millis);

        void forget(UUID player);

        Set<UUID> players();

        default void backup() {
        }
    }

    /** {@link Activity} over a {@link PrivacyPrefs} store. */
    public static Activity activity(PrivacyPrefs prefs, Runnable backup) {
        return new Activity() {
            @Override
            public Optional<Long> lastSeen(UUID player) {
                return prefs.lastSeen(player);
            }

            @Override
            public void seen(UUID player, long millis) {
                prefs.seen(player, millis);
            }

            @Override
            public void restoreSeen(UUID player, long millis) {
                prefs.restoreSeen(player, millis);
            }

            @Override
            public void forget(UUID player) {
                prefs.forget(player);
            }

            @Override
            public Set<UUID> players() {
                return prefs.players();
            }

            @Override
            public void backup() {
                backup.run();
            }
        };
    }

    private RetentionSweep() {
    }

    /**
     * One sweep.
     *
     * @return {@code purged} = inactive owners fully purged plus old rows removed; {@code failed} = owners whose purge
     *         failed somewhere (kept for a retry) plus store steps that failed
     */
    public static PlayerDataErasure.PurgeResult run(List<? extends Store> stores, Activity activity,
            Predicate<UUID> online, long nowMillis, long retentionMillis) {
        return run(stores, activity, online, owner -> { }, nowMillis, retentionMillis);
    }

    /**
     * One sweep, unbinding each inactive owner's <b>loaded</b> companions and loaded Bay records first (ownerless
     * Capsules, no tombstone - {@code unbindLoaded}), so a companion in a chunk that stays loaded (spawn chunks, a
     * chunk loader) cannot re-register the owner a second after their rows were purged. Companions in unloaded chunks
     * are adopted again when they load, as after any purge.
     */
    public static PlayerDataErasure.PurgeResult run(List<? extends Store> stores, Activity activity,
            Predicate<UUID> online, java.util.function.Consumer<UUID> unbindLoaded, long nowMillis,
            long retentionMillis) {
        Set<UUID> owners = new LinkedHashSet<>(activity.players());
        for (Store store : stores) {
            try {
                owners.addAll(store.owners());
            } catch (RuntimeException | LinkageError failure) {
                warn("listing owners", store.name(), failure);
            }
        }
        Set<Store> changed = new LinkedHashSet<>();
        boolean activityChanged = false;
        int purged = 0;
        int failed = 0;
        for (UUID owner : owners) {
            if (online.test(owner)) {
                activity.seen(owner, nowMillis);
                activityChanged = true;
                continue;
            }
            Optional<Long> seen = activity.lastSeen(owner);
            if (seen.isEmpty() || seen.get() <= 0L) {
                activity.seen(owner, nowMillis); // data from before the clock: it starts now
                activityChanged = true;
                continue;
            }
            if (nowMillis - seen.get() <= retentionMillis) {
                continue;
            }
            boolean ok = true;
            try {
                unbindLoaded.accept(owner);
            } catch (RuntimeException | LinkageError failure) {
                ok = false;
                warn("unbinding an inactive owner's loaded companions", "world", failure);
            }
            for (Store store : stores) {
                try {
                    store.purgeOwner(owner);
                    changed.add(store);
                } catch (RuntimeException | LinkageError failure) {
                    ok = false;
                    warn("purging an inactive owner", store.name(), failure);
                }
            }
            activityChanged = true;
            if (ok) {
                activity.forget(owner);
                purged++;
            } else {
                activity.restoreSeen(owner, seen.get());
                failed++;
            }
        }
        for (Store store : stores) {
            try {
                int removed = store.purgeUntouched(nowMillis, retentionMillis);
                if (removed > 0) {
                    purged += removed;
                    changed.add(store);
                }
            } catch (RuntimeException | LinkageError failure) {
                failed++;
                warn("purging old rows", store.name(), failure);
            }
        }
        for (Store store : changed) {
            try {
                store.backup();
            } catch (RuntimeException | LinkageError failure) {
                failed++;
                warn("refreshing the backup", store.name(), failure);
            }
        }
        if (activityChanged) {
            try {
                activity.backup();
            } catch (RuntimeException | LinkageError failure) {
                failed++;
                warn("refreshing the backup", "privacy", failure);
            }
        }
        return new PlayerDataErasure.PurgeResult(purged, failed);
    }

    private static void warn(String what, String store, Throwable failure) {
        NeroCompanionCommon.LOGGER.warn("[NeroCompanion] Retention sweep: {} failed in store '{}' ({}); retrying later",
                what, store, failure.getClass().getName());
    }
}
