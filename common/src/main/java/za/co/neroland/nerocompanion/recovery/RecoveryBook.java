package za.co.neroland.nerocompanion.recovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;
import za.co.neroland.nerocompanion.scheduler.LiveCompanionCounter;
import za.co.neroland.nerocompanion.scheduler.RegionKey;

/**
 * The Recovery Ledger's state machine, as plain Java (the SavedData wrapper is {@code data.RecoveryLedger}).
 * One entry per {@code companionId}; every transition checks the current state and moves it in one call on the
 * server thread, so a companion can never be in two places: deploy from a Capsule (CAPSULE with the matching ticket
 * → LIVE), recall (LIVE as that entity → CAPSULE, new ticket), park (LIVE as that entity → PARKED), unpark (PARKED at
 * that Bay → LIVE), Bay break (PARKED at that Bay → CAPSULE), death or unseen (LIVE → LOST), reclaim (LOST →
 * CAPSULE), found again (LOST → LIVE).
 *
 * <p><b>Tombstones.</b> Erasing a player replaces each of their entries with an owner-less tombstone (companion id
 * and erasure time only - a random id, no link to a person). Only a tombstone makes a loaded companion read as
 * {@link Verdict#ERASED}; an entity or Bay record the ledger simply does not know (a ledger restored from an older
 * backup, a retention purge, a world from before the ledger) is <em>adopted</em> under the owner it carries.
 *
 * <p>Also the cap source ({@link LiveCompanionCounter}): LIVE entries per owner; per 8x8-chunk region only the LIVE
 * entries seen recently ({@link #counter}), so departed players' unloaded companions do not fill a region for good.
 *
 * <p>Privacy: holds owner UUIDs, a last-known position per companion and (in the snapshot) its home, bound
 * container and mining area. Shown only to the owner, erased by owner ({@link #forgetOwner}), CAPSULE/LOST entries and
 * tombstones purged by retention ({@link #purgeUntouched}); LIVE entries untouched for the offline bound become LOST
 * ({@link #sweepLost}) and then follow the same retention.
 */
public final class RecoveryBook implements LiveCompanionCounter {

    /** Snapshot refresh cadence while LIVE: 10 seconds. */
    public static final long TOUCH_INTERVAL_TICKS = 200L;
    /** Most tombstones kept; the oldest go first. */
    public static final int MAX_TOMBSTONES = 8192;

    /** Why a check failed: a reason lang key. */
    public static final String STALE_CAPSULE = "gui.nerocompanion.reason.stale_capsule";
    public static final String STALE_RECORD = "gui.nerocompanion.reason.stale_record";
    public static final String ALREADY_OUT = "authority.nerocompanion.already_deployed";
    public static final String NOT_LOST = "gui.nerocompanion.reason.not_lost";

    /** What an entity learns when it asks the ledger who it is. */
    public enum Verdict {
        /** The ledger agrees: LIVE as this entity. */
        OK,
        /** No entry and no tombstone: re-register it under the owner it carries. */
        ADOPT,
        /** It was LOST (unloaded too long), or PARKED in a Bay that no longer holds it, and is here: LIVE again. */
        FOUND,
        /** Another copy is the real one (LIVE elsewhere, in a Capsule or Bay, or reclaimed): discard this one. */
        STALE,
        /** A tombstone: its owner was erased; unbind it. */
        ERASED
    }

    /** What a loaded Bay says about a PARKED companion. */
    public enum BayState {
        /** The Bay is not loaded here (or another dimension): unknown. */
        UNKNOWN,
        /** The Bay is loaded and holds the companion. */
        HOLDS,
        /** The Bay's position is loaded and it does not hold the companion (or is no Bay any more). */
        MISSING
    }

    /** Asks the world about a Bay. */
    @FunctionalInterface
    public interface BayProbe {
        BayState probe(HomePos bay, UUID companionId);

        BayProbe UNKNOWN = (bay, id) -> BayState.UNKNOWN;
    }

    /** The outcome of a Bay hand-out (the Bay is breaking). */
    public enum HandOut {
        /** Mint a Capsule with the returned ticket. */
        CAPSULE,
        /** The record's owner was erased: an ownerless Capsule, no ledger entry. */
        UNBIND,
        /** A stale copy: nothing. */
        REFUSE
    }

    private final Map<UUID, LedgerEntry> entries = new LinkedHashMap<>();
    /** companion id → erasure time (wall-clock millis). No owner, no position. */
    private final Map<UUID, Long> tombstones = new LinkedHashMap<>();
    private final LongSupplier tickets;
    private boolean dirty;
    private Runnable onChange = () -> { };

    public RecoveryBook() {
        this(() -> {
            long value;
            do {
                value = ThreadLocalRandom.current().nextLong();
            } while (value == 0L);
            return value;
        });
    }

    /** With a ticket source (tests). Tickets must be non-zero. */
    public RecoveryBook(LongSupplier tickets) {
        this.tickets = tickets;
    }

    public RecoveryBook(Collection<LedgerEntry> loaded) {
        this(loaded, Map.of());
    }

    /** A loaded ledger. */
    public RecoveryBook(Collection<LedgerEntry> loaded, Map<UUID, Long> tombstones) {
        this();
        for (LedgerEntry entry : loaded) {
            this.entries.put(entry.companionId(), entry);
        }
        this.tombstones.putAll(tombstones);
    }

    // =================================================================================================
    // Reads
    // =================================================================================================

    public Optional<LedgerEntry> get(UUID companionId) {
        return Optional.ofNullable(this.entries.get(companionId));
    }

    public List<LedgerEntry> all() {
        return List.copyOf(this.entries.values());
    }

    public Map<UUID, Long> tombstones() {
        return Map.copyOf(this.tombstones);
    }

    public boolean isTombstoned(UUID companionId) {
        return this.tombstones.containsKey(companionId);
    }

    public List<LedgerEntry> entriesOf(UUID owner) {
        List<LedgerEntry> out = new ArrayList<>();
        for (LedgerEntry entry : this.entries.values()) {
            if (entry.owner().equals(owner)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The owner's LOST companions, oldest loss first. */
    public List<LedgerEntry> lostOf(UUID owner) {
        List<LedgerEntry> out = new ArrayList<>();
        for (LedgerEntry entry : this.entries.values()) {
            if (entry.owner().equals(owner) && entry.is(State.LOST)) {
                out.add(entry);
            }
        }
        out.sort((a, b) -> Long.compare(a.lostAtTick(), b.lostAtTick()));
        return out;
    }

    public boolean holdsFor(UUID owner) {
        return this.entries.values().stream().anyMatch(entry -> entry.owner().equals(owner));
    }

    /** Every owner with an entry (the inactive-owner retention sweep). */
    public java.util.Set<UUID> owners() {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        for (LedgerEntry entry : this.entries.values()) {
            out.add(entry.owner());
        }
        return out;
    }

    /** Whether there is any erasure tombstone (the Capsule-name sweep has nothing to do otherwise). */
    public boolean hasTombstones() {
        return !this.tombstones.isEmpty();
    }

    /**
     * Retention of an inactive owner: every entry of {@code owner} is removed, with <b>no</b> tombstone - this is not
     * an erasure, so a companion of theirs that loads later is adopted again, as after any retention purge.
     *
     * @return how many entries were removed
     */
    public int purgeOwner(UUID owner) {
        int before = this.entries.size();
        this.entries.values().removeIf(entry -> entry.owner().equals(owner));
        int removed = before - this.entries.size();
        if (removed > 0) {
            this.changed();
        }
        return removed;
    }

    public int size() {
        return this.entries.size();
    }

    /** Whether anything changed since the last {@link #clearDirty()}. */
    public boolean dirty() {
        return this.dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /** Called after every write (the SavedData wrapper marks itself dirty). */
    public void onChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    private void changed() {
        this.dirty = true;
        this.onChange.run();
    }

    // --- caps ------------------------------------------------------------------------------------

    @Override
    public int liveFor(UUID owner, @Nullable UUID except) {
        int count = 0;
        for (LedgerEntry entry : this.entries.values()) {
            if (entry.is(State.LIVE) && entry.owner().equals(owner) && !entry.companionId().equals(except)) {
                count++;
            }
        }
        return count;
    }

    /** Every LIVE entry in {@code region}, however old (see {@link #counter} for the cap's view). */
    @Override
    public int liveIn(RegionKey region, @Nullable UUID except) {
        return this.liveIn(region, except, Long.MIN_VALUE);
    }

    private int liveIn(RegionKey region, @Nullable UUID except, long seenSince) {
        int count = 0;
        for (LedgerEntry entry : this.entries.values()) {
            if (entry.is(State.LIVE) && !entry.companionId().equals(except) && entry.lastKnown().isPresent()
                    && entry.lastSeenTick() >= seenSince && regionOf(entry.lastKnown().get()).equals(region)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean isLiveElsewhere(UUID companionId, @Nullable UUID entityId) {
        LedgerEntry entry = this.entries.get(companionId);
        return entry != null && entry.is(State.LIVE)
                && (entityId == null || entry.entity().map(id -> !id.equals(entityId)).orElse(true));
    }

    /**
     * The caps' view at {@code now}: the player cap counts every LIVE entry of the owner; the region cap counts only
     * LIVE entries seen within {@code recentTicks} (loaded companions report every 10 s), so companions left in
     * unloaded chunks - above all those of players who left - stop filling a region.
     */
    public LiveCompanionCounter counter(long now, long recentTicks) {
        long since = now - Math.max(TOUCH_INTERVAL_TICKS * 2L, recentTicks);
        RecoveryBook book = this;
        return new LiveCompanionCounter() {
            @Override
            public int liveFor(UUID owner, @Nullable UUID except) {
                return book.liveFor(owner, except);
            }

            @Override
            public int liveIn(RegionKey region, @Nullable UUID except) {
                return book.liveIn(region, except, since);
            }

            @Override
            public boolean isLiveElsewhere(UUID companionId, @Nullable UUID entityId) {
                return book.isLiveElsewhere(companionId, entityId);
            }
        };
    }

    public static RegionKey regionOf(HomePos at) {
        return RegionKey.ofBlock(at.dimension().toString(), at.pos().getX(), at.pos().getZ());
    }

    // =================================================================================================
    // Checks (no side effects)
    // =================================================================================================

    /** A brand-new companion (blueprint, egg, hatch, debug): its id must be unknown. Null = allowed. */
    @Nullable
    public String checkFresh(UUID companionId) {
        return this.entries.containsKey(companionId) ? ALREADY_OUT : null;
    }

    /**
     * A Capsule deploy. Allowed when the ledger has no entry (a Capsule from before the ledger, an erased owner's
     * ownerless Capsule, or one whose entry retention purged) or the entry is CAPSULE with this ticket. Null = allowed.
     */
    @Nullable
    public String checkCapsule(UUID companionId, long ticket) {
        LedgerEntry entry = this.entries.get(companionId);
        if (entry == null) {
            return null;
        }
        if (entry.is(State.LIVE)) {
            return ALREADY_OUT;
        }
        return entry.is(State.CAPSULE) && entry.ticket() != 0L && entry.ticket() == ticket ? null : STALE_CAPSULE;
    }

    /** A Bay unpark from {@code bay}. Null = allowed (no entry: adopted on deploy). */
    @Nullable
    public String checkParked(UUID companionId, HomePos bay) {
        LedgerEntry entry = this.entries.get(companionId);
        if (entry == null) {
            return this.tombstones.containsKey(companionId) ? STALE_RECORD : null;
        }
        return entry.is(State.PARKED) && entry.bay().isPresent() && entry.bay().get().equals(bay) ? null
                : STALE_RECORD;
    }

    /** What a loaded entity is (see {@link Verdict}); {@code bays} answers for PARKED entries. */
    public Verdict verdict(UUID companionId, UUID entityId, @Nullable UUID owner, BayProbe bays) {
        LedgerEntry entry = this.entries.get(companionId);
        if (entry == null) {
            return this.tombstones.containsKey(companionId) ? Verdict.ERASED : Verdict.ADOPT;
        }
        if (owner == null || !entry.owner().equals(owner)) {
            return Verdict.STALE;
        }
        if (entry.liveAs(entityId)) {
            return Verdict.OK;
        }
        if (entry.is(State.LOST) && entry.entity().isPresent() && entry.entity().get().equals(entityId)) {
            return Verdict.FOUND;
        }
        if (entry.is(State.PARKED) && entry.bay().isPresent()
                && bays.probe(entry.bay().get(), companionId) == BayState.MISSING) {
            // The Bay that should hold it does not (moved, reset, crash skew): this entity is the companion.
            return Verdict.FOUND;
        }
        return Verdict.STALE;
    }

    // =================================================================================================
    // Transitions
    // =================================================================================================

    /** Deploy / unpark / adopt / found: LIVE as {@code entityId} at {@code at}. The caller checked the source. */
    public void live(CompanionRecord record, UUID entityId, HomePos at, long now, long millis) {
        LedgerEntry old = this.entries.get(record.companionId());
        long ticket = old == null ? 0L : old.ticket();
        this.tombstones.remove(record.companionId());
        this.put(new LedgerEntry(record.companionId(), record.ownerId(), State.LIVE, ticket, Optional.of(entityId),
                Optional.empty(), Optional.of(at), now, now, 0L, millis, ReclaimRecords.forLedger(record)));
    }

    /** Whether a LIVE entry's 10-second refresh is due (cheap; lets the caller skip building a record). */
    public boolean touchDue(UUID companionId, long now) {
        LedgerEntry entry = this.entries.get(companionId);
        return entry != null && entry.is(State.LIVE)
                && (now - entry.lastSeenTick() >= TOUCH_INTERVAL_TICKS || now < entry.lastSeenTick());
    }

    /**
     * The 10-second refresh while LIVE: snapshot and last-known position. Writes only when the entry is LIVE as
     * this entity and either {@code force} or {@link #TOUCH_INTERVAL_TICKS} passed.
     *
     * @return whether it wrote
     */
    public boolean touch(CompanionRecord record, UUID entityId, HomePos at, long now, long millis, boolean force) {
        LedgerEntry entry = this.entries.get(record.companionId());
        if (entry == null || !entry.liveAs(entityId)) {
            return false;
        }
        if (!force && now - entry.lastSeenTick() < TOUCH_INTERVAL_TICKS && now >= entry.lastSeenTick()) {
            return false;
        }
        this.put(new LedgerEntry(entry.companionId(), record.ownerId(), State.LIVE, entry.ticket(), entry.entity(),
                Optional.empty(), Optional.of(at), now, Math.max(now, entry.clockFrom()), 0L, millis,
                ReclaimRecords.forLedger(record)));
        return true;
    }

    /**
     * Recall: LIVE as {@code entityId} (or unknown) → CAPSULE with a new ticket.
     *
     * @return the new ticket, or 0 when the ledger says this entity is not the companion (nothing changed)
     */
    public long recall(CompanionRecord record, UUID entityId, long now, long millis) {
        LedgerEntry entry = this.entries.get(record.companionId());
        if (entry != null && !entry.liveAs(entityId)) {
            return 0L;
        }
        return this.toCapsule(record, now, millis);
    }

    /**
     * Park: LIVE as {@code entityId} (or unknown) → PARKED in {@code bay}.
     *
     * @return false when the ledger says this entity is not the companion (nothing changed)
     */
    public boolean park(CompanionRecord record, UUID entityId, HomePos bay, long now, long millis) {
        LedgerEntry entry = this.entries.get(record.companionId());
        if (entry != null && !entry.liveAs(entityId)) {
            return false;
        }
        this.adoptParked(record, bay, now, millis);
        return true;
    }

    /** A Bay record the ledger did not know (reconcile): PARKED at {@code bay}. */
    public void adoptParked(CompanionRecord record, HomePos bay, long now, long millis) {
        LedgerEntry old = this.entries.get(record.companionId());
        long ticket = old == null ? 0L : old.ticket();
        this.tombstones.remove(record.companionId());
        this.put(new LedgerEntry(record.companionId(), record.ownerId(), State.PARKED, ticket, Optional.empty(),
                Optional.of(bay), Optional.of(bay), now, now, 0L, millis, ReclaimRecords.forLedger(record)));
    }

    /** The result of {@link #handOut}: what to do, and the ticket for {@link HandOut#CAPSULE}. */
    public record HandOutResult(HandOut action, long ticket) {
    }

    /** The Bay at {@code bay} is breaking: may this record become a Capsule? */
    public HandOutResult handOut(CompanionRecord record, HomePos bay, long now, long millis) {
        LedgerEntry entry = this.entries.get(record.companionId());
        if (entry == null) {
            if (this.tombstones.containsKey(record.companionId())) {
                return new HandOutResult(HandOut.UNBIND, 0L);
            }
            return new HandOutResult(HandOut.CAPSULE, this.toCapsule(record, now, millis));
        }
        if (!entry.is(State.PARKED) || entry.bay().isEmpty() || !entry.bay().get().equals(bay)) {
            return new HandOutResult(HandOut.REFUSE, 0L);
        }
        return new HandOutResult(HandOut.CAPSULE, this.toCapsule(record, now, millis));
    }

    /**
     * Unchecked: the companion goes into a new Capsule (reclaim; tests). Game paths use {@link #recall} and
     * {@link #handOut}, which check first.
     *
     * @return the new Capsule's ticket
     */
    public long toCapsule(CompanionRecord record, long now, long millis) {
        LedgerEntry old = this.entries.get(record.companionId());
        long ticket = this.tickets.getAsLong();
        Optional<HomePos> at = old == null ? Optional.empty() : old.lastKnown();
        this.tombstones.remove(record.companionId());
        this.put(new LedgerEntry(record.companionId(), record.ownerId(), State.CAPSULE, ticket, Optional.empty(),
                Optional.empty(), at, now, now, 0L, millis, ReclaimRecords.forLedger(record)));
        return ticket;
    }

    /** Unchecked park (tests, adoption): see {@link #park}. */
    public void parked(CompanionRecord record, HomePos bay, long now, long millis) {
        this.adoptParked(record, bay, now, millis);
    }

    /**
     * The entity died: LIVE → LOST at once (it can be reclaimed). Only when LIVE as that entity.
     *
     * @return whether it became LOST
     */
    public boolean died(CompanionRecord record, UUID entityId, HomePos at, long now, long millis) {
        LedgerEntry entry = this.entries.get(record.companionId());
        if (entry == null || !entry.liveAs(entityId)) {
            return false;
        }
        this.put(new LedgerEntry(entry.companionId(), entry.owner(), State.LOST, entry.ticket(), entry.entity(),
                Optional.empty(), Optional.of(at), now, now, now, millis,
                ReclaimRecords.forLedger(record).withHealth(CompanionRecord.FULL_HEALTH)));
        return true;
    }

    /**
     * LIVE entries that have not reported for {@code lostTicks} while the owner is online, or for {@code offlineMillis}
     * of wall-clock time whatever the owner does, become LOST. The offline-aged ones lose their last-known position
     * (the owner has not seen them for that long; the snapshot is kept for a reclaim and follows retention).
     *
     * @return the entries that just became LOST
     */
    public List<LedgerEntry> sweepLost(long now, long lostTicks, Predicate<UUID> ownerOnline, long millis,
            long offlineMillis) {
        List<LedgerEntry> lost = new ArrayList<>();
        for (LedgerEntry entry : List.copyOf(this.entries.values())) {
            if (!entry.is(State.LIVE)) {
                continue;
            }
            boolean online = ownerOnline.test(entry.owner());
            boolean unseenOnline = online && now - Math.max(entry.lastSeenTick(), entry.clockFrom()) > lostTicks;
            boolean agedOut = millis - entry.touchedAt() > offlineMillis;
            if (unseenOnline || agedOut) {
                LedgerEntry next = new LedgerEntry(entry.companionId(), entry.owner(), State.LOST, entry.ticket(),
                        entry.entity(), Optional.empty(), unseenOnline ? entry.lastKnown() : Optional.empty(),
                        entry.lastSeenTick(), entry.clockFrom(), now, millis, entry.snapshot());
                this.put(next);
                lost.add(next);
            }
        }
        return lost;
    }

    /** {@link #sweepLost} without the wall-clock bound (tests of the online rule). */
    public List<LedgerEntry> sweepLost(long now, long lostTicks, Predicate<UUID> ownerOnline, long millis) {
        return this.sweepLost(now, lostTicks, ownerOnline, millis, Long.MAX_VALUE);
    }

    /**
     * PARKED entries whose Bay is loaded and does not hold them become LOST (reclaimable): a moved, reset or edited
     * Bay can never strand a companion.
     *
     * @return the entries that just became LOST
     */
    public List<LedgerEntry> sweepOrphanedParked(BayProbe bays, long now, long millis) {
        List<LedgerEntry> lost = new ArrayList<>();
        for (LedgerEntry entry : List.copyOf(this.entries.values())) {
            if (entry.is(State.PARKED) && entry.bay().isPresent()
                    && bays.probe(entry.bay().get(), entry.companionId()) == BayState.MISSING) {
                lost.add(this.toLost(entry, now, millis));
            }
        }
        return lost;
    }

    /**
     * Operator release: any state but LOST becomes LOST, for the cases the sweeps cannot see.
     *
     * @return whether it changed
     */
    public boolean release(UUID companionId, long now, long millis) {
        LedgerEntry entry = this.entries.get(companionId);
        if (entry == null || entry.is(State.LOST)) {
            return false;
        }
        this.toLost(entry, now, millis);
        return true;
    }

    private LedgerEntry toLost(LedgerEntry entry, long now, long millis) {
        LedgerEntry next = new LedgerEntry(entry.companionId(), entry.owner(), State.LOST, entry.ticket(),
                entry.entity(), Optional.empty(), entry.lastKnown(), entry.lastSeenTick(), entry.clockFrom(), now,
                millis, entry.snapshot());
        this.put(next);
        return next;
    }

    /** The owner logged in: their LIVE companions' lost clocks start now (time offline never counts). */
    public void ownerJoined(UUID owner, long now) {
        for (LedgerEntry entry : List.copyOf(this.entries.values())) {
            if (entry.owner().equals(owner) && entry.is(State.LIVE) && entry.clockFrom() < now) {
                this.put(new LedgerEntry(entry.companionId(), entry.owner(), entry.state(), entry.ticket(),
                        entry.entity(), entry.bay(), entry.lastKnown(), entry.lastSeenTick(), now,
                        entry.lostAtTick(), entry.touchedAt(), entry.snapshot()));
            }
        }
    }

    /** The outcome of a reclaim: the record to put in a Capsule and its ticket, or a refusal reason. */
    public record Reclaim(@Nullable CompanionRecord record, long ticket, String reason) {
        public boolean ok() {
            return this.record != null;
        }
    }

    /**
     * {@code owner} reclaims LOST {@code companionId}: LOST → CAPSULE with a new ticket. A second reclaim finds
     * CAPSULE and is refused.
     */
    public Reclaim reclaim(UUID owner, UUID companionId, long now, long millis) {
        LedgerEntry entry = this.entries.get(companionId);
        if (entry == null || !entry.owner().equals(owner)) {
            return new Reclaim(null, 0L, "gui.nerocompanion.reason.not_owner");
        }
        if (!entry.is(State.LOST)) {
            return new Reclaim(null, 0L, NOT_LOST);
        }
        CompanionRecord record = entry.snapshot();
        long ticket = this.toCapsule(record, now, millis);
        return new Reclaim(record, ticket, "");
    }

    /** Debug clear: forget one companion entirely (no tombstone: it is gone on purpose). */
    public void forget(UUID companionId) {
        if (this.entries.remove(companionId) != null) {
            this.changed();
        }
    }

    /**
     * Erasure: every entry of {@code owner} is replaced by an owner-less tombstone (id and time only).
     *
     * @return how many entries were removed
     */
    public int forgetOwner(UUID owner, long millis) {
        int removed = 0;
        for (LedgerEntry entry : List.copyOf(this.entries.values())) {
            if (entry.owner().equals(owner)) {
                this.entries.remove(entry.companionId());
                this.tombstones.put(entry.companionId(), millis);
                removed++;
            }
        }
        this.trimTombstones();
        if (removed > 0) {
            this.changed();
        }
        return removed;
    }

    /** {@link #forgetOwner(UUID, long)} stamped now. */
    public int forgetOwner(UUID owner) {
        return this.forgetOwner(owner, System.currentTimeMillis());
    }

    private void trimTombstones() {
        while (this.tombstones.size() > MAX_TOMBSTONES) {
            UUID oldest = null;
            long at = Long.MAX_VALUE;
            for (Map.Entry<UUID, Long> e : this.tombstones.entrySet()) {
                if (e.getValue() < at) {
                    at = e.getValue();
                    oldest = e.getKey();
                }
            }
            this.tombstones.remove(oldest);
        }
    }

    /**
     * Retention: CAPSULE and LOST entries, and tombstones, not written for {@code retentionMillis} are purged. LIVE
     * and PARKED entries stay while the companion exists (LIVE ones age to LOST first, see {@link #sweepLost}).
     *
     * @return how many were purged
     */
    public int purgeUntouched(long nowMillis, long retentionMillis) {
        int before = this.entries.size() + this.tombstones.size();
        this.entries.values().removeIf(entry -> (entry.is(State.CAPSULE) || entry.is(State.LOST))
                && nowMillis - entry.touchedAt() > retentionMillis);
        this.tombstones.values().removeIf(at -> nowMillis - at > retentionMillis);
        int removed = before - this.entries.size() - this.tombstones.size();
        if (removed > 0) {
            this.changed();
        }
        return removed;
    }

    private void put(LedgerEntry entry) {
        this.entries.put(entry.companionId(), entry);
        this.changed();
    }
}
