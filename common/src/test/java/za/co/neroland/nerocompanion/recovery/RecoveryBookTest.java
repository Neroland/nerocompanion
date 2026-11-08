package za.co.neroland.nerocompanion.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.nbt.NbtOps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.job.JobData;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;
import za.co.neroland.nerocompanion.scheduler.CapPolicy;
import za.co.neroland.nerocompanion.scheduler.CompanionLimits;
import za.co.neroland.nerocompanion.scheduler.RegionKey;

/**
 * The Recovery Ledger's state machine: the 10-second cadence and the lost rule; every transition
 * (deploy from a Capsule, recall, park, unpark, reclaim, Bay break, death); duplicate attempts (reclaim twice, an old
 * Capsule copy, a creative-copied Capsule after a restart, a stale Bay record, a stale entity); the caps across a
 * simulated restart; retention and erasure.
 */
class RecoveryBookTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final HomePos HERE = new HomePos(OVERWORLD, new BlockPos(10, 64, 10));
    private static final HomePos BAY = new HomePos(OVERWORLD, new BlockPos(0, 64, 0));
    private static final long LOST_TICKS = 30L * 60L * 20L;

    private final AtomicLong nextTicket = new AtomicLong(100L);
    private final UUID owner = UUID.randomUUID();

    private RecoveryBook book() {
        return new RecoveryBook(nextTicket::incrementAndGet);
    }

    private CompanionRecord record(UUID owner) {
        CompoundTag job = new CompoundTag();
        job.putString(JobData.KEY_JOB_TYPE, "nerocompanion:fetch");
        job.put(JobData.KEY_JOB, new CompoundTag());
        job.put(JobData.KEY_CARGO, new CompoundTag());
        job.putString(JobData.KEY_BOUND, "kept");
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"), CompanionMode.CARRY, 5_000L,
                List.of(new ChipStack(Identifier.fromNamespaceAndPath("nerocompanion", "speed"), 2)), 420,
                Optional.of("Rusty"), Optional.of(Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot/rust")),
                Optional.of(Identifier.fromNamespaceAndPath("nerocompanion", "cap")), 77L, Optional.of(BAY), job, 9.0F);
    }

    /** A save/load cycle: what a server restart does to the ledger. */
    private RecoveryBook restart(RecoveryBook before) {
        var encoded = LedgerEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, before.all()).getOrThrow();
        return new RecoveryBook(LedgerEntry.CODEC.listOf().parse(NbtOps.INSTANCE, encoded).getOrThrow(),
                before.tombstones());
    }

    // =================================================================================================
    // Cadence and the lost rule
    // =================================================================================================

    @Test
    void touchesOnTheTenSecondCadenceOnlyUnlessForced() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID entity = UUID.randomUUID();
        book.live(r, entity, HERE, 1_000L, 0L);
        assertFalse(book.touchDue(r.companionId(), 1_100L));
        assertFalse(book.touch(r, entity, HERE, 1_100L, 0L, false));
        assertTrue(book.touch(r, entity, HERE, 1_100L, 0L, true), "a mode change writes at once");
        assertFalse(book.touchDue(r.companionId(), 1_299L));
        assertTrue(book.touchDue(r.companionId(), 1_300L));
        assertTrue(book.touch(r, entity, HERE, 1_300L, 0L, false));
        assertEquals(1_300L, book.get(r.companionId()).orElseThrow().lastSeenTick());
        // Another entity with the same id never refreshes the entry.
        assertFalse(book.touch(r, UUID.randomUUID(), HERE, 2_000L, 0L, true));
    }

    @Test
    void unseenWhileOwnerOnlineBecomesLostAfterTheConfiguredTime() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        assertTrue(book.sweepLost(LOST_TICKS, LOST_TICKS, o -> true, 0L).isEmpty(), "not yet: exactly the limit");
        List<LedgerEntry> lost = book.sweepLost(LOST_TICKS + 1, LOST_TICKS, o -> true, 0L);
        assertEquals(1, lost.size());
        assertEquals(State.LOST, book.get(r.companionId()).orElseThrow().state());
        assertEquals(LOST_TICKS + 1, book.get(r.companionId()).orElseThrow().lostAtTick());
    }

    @Test
    void timeOfflineNeverCountsTowardLost() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        // The owner is offline for a long time: nothing happens.
        assertTrue(book.sweepLost(10 * LOST_TICKS, LOST_TICKS, o -> false, 0L).isEmpty());
        // They log in: the clock starts now.
        book.ownerJoined(owner, 10 * LOST_TICKS);
        assertTrue(book.sweepLost(10 * LOST_TICKS + 100, LOST_TICKS, o -> true, 0L).isEmpty());
        assertEquals(1, book.sweepLost(11 * LOST_TICKS + 1, LOST_TICKS, o -> true, 0L).size());
    }

    @Test
    void aKilledCompanionIsLostAtOnceAtFullHealthWithoutCargoOrJob() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID entity = UUID.randomUUID();
        book.live(r, entity, HERE, 0L, 0L);
        assertTrue(book.died(r, entity, HERE, 50L, 0L));
        LedgerEntry entry = book.get(r.companionId()).orElseThrow();
        assertEquals(State.LOST, entry.state());
        assertTrue(entry.snapshot().fullHealth());
        CompoundTag job = entry.snapshot().jobData();
        assertFalse(job.contains(JobData.KEY_CARGO), "the ledger never keeps cargo (it dropped where it died)");
        assertFalse(job.contains(JobData.KEY_JOB), "a carried Fetch Ball is the job: not restored");
        assertTrue(job.contains(JobData.KEY_BOUND), "the bound container is the owner's setting: kept");
        // A death reported by a stale copy changes nothing.
        assertFalse(book.died(r, UUID.randomUUID(), HERE, 60L, 0L));
    }

    // =================================================================================================
    // Transitions and duplicate attempts
    // =================================================================================================

    @Test
    void capsuleRoundTripAndOldCapsuleCopiesAreInert() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID first = UUID.randomUUID();
        assertNull(book.checkFresh(r.companionId()));
        book.live(r, first, HERE, 0L, 0L);
        assertEquals(RecoveryBook.ALREADY_OUT, book.checkFresh(r.companionId()));

        long ticket = book.toCapsule(r, 10L, 0L);
        assertEquals(State.CAPSULE, book.get(r.companionId()).orElseThrow().state());
        assertNull(book.checkCapsule(r.companionId(), ticket), "the Capsule just minted deploys");
        assertEquals(RecoveryBook.STALE_CAPSULE, book.checkCapsule(r.companionId(), ticket + 1));
        assertEquals(RecoveryBook.STALE_CAPSULE, book.checkCapsule(r.companionId(), 0L), "no ticket, known id");

        // Deploy, recall again: a new ticket; the old Capsule (and every copy of it) is dead.
        book.live(r, UUID.randomUUID(), HERE, 20L, 0L);
        assertEquals(RecoveryBook.ALREADY_OUT, book.checkCapsule(r.companionId(), ticket));
        long second = book.toCapsule(r, 30L, 0L);
        assertNotEquals(ticket, second);
        assertEquals(RecoveryBook.STALE_CAPSULE, book.checkCapsule(r.companionId(), ticket));
        assertNull(book.checkCapsule(r.companionId(), second));
    }

    @Test
    void aCreativeCopiedCapsuleCannotDeployTwiceEvenAcrossARestart() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        long ticket = book.toCapsule(r, 10L, 0L);
        // Original and copy carry the same ticket. Restart the server.
        RecoveryBook after = restart(book);
        assertNull(after.checkCapsule(r.companionId(), ticket), "one of the two may deploy");
        after.live(r, UUID.randomUUID(), HERE, 20L, 0L);
        assertEquals(RecoveryBook.ALREADY_OUT, after.checkCapsule(r.companionId(), ticket), "the copy may not");
        RecoveryBook again = restart(after);
        assertEquals(RecoveryBook.ALREADY_OUT, again.checkCapsule(r.companionId(), ticket), "nor after a restart");
        long newTicket = again.toCapsule(r, 30L, 0L);
        assertEquals(RecoveryBook.STALE_CAPSULE, again.checkCapsule(r.companionId(), ticket),
                "after the recall both old Capsules are dead");
        assertNull(again.checkCapsule(r.companionId(), newTicket));
    }

    @Test
    void legacyCapsulesDeployOnlyWhileTheLedgerDoesNotKnowTheCompanion() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        assertNull(book.checkCapsule(r.companionId(), 0L));
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        assertEquals(RecoveryBook.ALREADY_OUT, book.checkCapsule(r.companionId(), 0L));
    }

    @Test
    void parkUnparkAndBayBreak() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        book.parked(r, BAY, 10L, 0L);
        assertEquals(State.PARKED, book.get(r.companionId()).orElseThrow().state());
        assertEquals(0, book.liveFor(owner, null), "parked companions do not count");
        assertNull(book.checkParked(r.companionId(), BAY));
        HomePos otherBay = new HomePos(OVERWORLD, new BlockPos(100, 64, 100));
        assertEquals(RecoveryBook.STALE_RECORD, book.checkParked(r.companionId(), otherBay),
                "a copied Bay record elsewhere is inert");
        // Unpark: LIVE; the Bay record is now stale.
        book.live(r, UUID.randomUUID(), HERE, 20L, 0L);
        assertEquals(RecoveryBook.STALE_RECORD, book.checkParked(r.companionId(), BAY));
        // Park again, then the Bay breaks: a Capsule with a ticket.
        book.parked(r, BAY, 30L, 0L);
        long ticket = book.toCapsule(r, 40L, 0L);
        assertNull(book.checkCapsule(r.companionId(), ticket));
        assertEquals(RecoveryBook.STALE_RECORD, book.checkParked(r.companionId(), BAY),
                "a second hand-out of the same Bay record is refused");
    }

    @Test
    void reclaimRestoresEveryFieldAndCanNeverHappenTwice() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID entity = UUID.randomUUID();
        book.live(r, entity, HERE, 0L, 0L);
        book.sweepLost(LOST_TICKS + 1, LOST_TICKS, o -> true, 0L);

        RecoveryBook.Reclaim stranger = book.reclaim(UUID.randomUUID(), r.companionId(), 100L, 0L);
        assertFalse(stranger.ok(), "only the owner reclaims");

        RecoveryBook.Reclaim first = book.reclaim(owner, r.companionId(), 100L, 0L);
        assertTrue(first.ok());
        CompanionRecord back = first.record();
        assertEquals(r.companionId(), back.companionId());
        assertEquals(r.ownerId(), back.ownerId());
        assertEquals(r.type(), back.type());
        assertEquals(r.mode(), back.mode());
        assertEquals(r.energyFe(), back.energyFe());
        assertEquals(r.chips(), back.chips());
        assertEquals(r.bondPoints(), back.bondPoints());
        assertEquals(r.name(), back.name());
        assertEquals(r.skin(), back.skin());
        assertEquals(r.hat(), back.hat());
        assertEquals(r.personalitySeed(), back.personalitySeed());
        assertEquals(r.home(), back.home());
        assertEquals(r.health(), back.health(), "an unloaded companion comes back as hurt as it was");
        assertFalse(back.jobData().contains(JobData.KEY_CARGO));
        assertNull(book.checkCapsule(r.companionId(), first.ticket()));

        RecoveryBook.Reclaim second = book.reclaim(owner, r.companionId(), 101L, 0L);
        assertFalse(second.ok(), "reclaim twice is impossible");
        assertEquals(RecoveryBook.NOT_LOST, second.reason());
        assertFalse(restart(book).reclaim(owner, r.companionId(), 102L, 0L).ok(), "also after a restart");
    }

    @Test
    void theEntityAsksWhoItIs() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID entity = UUID.randomUUID();
        RecoveryBook.BayProbe unknown = RecoveryBook.BayProbe.UNKNOWN;
        assertEquals(RecoveryBook.Verdict.ADOPT, book.verdict(r.companionId(), entity, owner, unknown));
        book.live(r, entity, HERE, 0L, 0L);
        assertEquals(RecoveryBook.Verdict.OK, book.verdict(r.companionId(), entity, owner, unknown));
        UUID copy = UUID.randomUUID();
        assertEquals(RecoveryBook.Verdict.STALE, book.verdict(r.companionId(), copy, owner, unknown),
                "a second entity with the same id is never LIVE too");
        assertEquals(RecoveryBook.Verdict.STALE, book.verdict(r.companionId(), entity, UUID.randomUUID(), unknown),
                "a copy owned by someone else is stale");
        // Unloaded too long → LOST; the same entity loads again → FOUND.
        book.sweepLost(LOST_TICKS + 1, LOST_TICKS, o -> true, 0L);
        assertEquals(RecoveryBook.Verdict.FOUND, book.verdict(r.companionId(), entity, owner, unknown));
        // Reclaimed instead → the old entity is stale when its chunk loads (its cargo drops there).
        book.reclaim(owner, r.companionId(), 200L, 0L);
        assertEquals(RecoveryBook.Verdict.STALE, book.verdict(r.companionId(), entity, owner, unknown));
    }

    // =================================================================================================
    // Erasure tombstones: only a tombstone unbinds; absence adopts
    // =================================================================================================

    @Test
    void onlyAnErasureTombstoneMakesAnEntityReadAsErased() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID entity = UUID.randomUUID();
        book.live(r, entity, HERE, 0L, 0L);
        assertEquals(1, book.forgetOwner(owner, 5L));
        assertFalse(book.holdsFor(owner));
        assertTrue(book.isTombstoned(r.companionId()));
        assertEquals(RecoveryBook.Verdict.ERASED,
                book.verdict(r.companionId(), entity, owner, RecoveryBook.BayProbe.UNKNOWN));
        assertEquals(RecoveryBook.Verdict.ERASED, restart(book).verdict(r.companionId(), entity, owner,
                RecoveryBook.BayProbe.UNKNOWN), "tombstones survive a restart");
        // The ownerless Capsule erasure dropped deploys for whoever finds it; that clears the tombstone.
        assertNull(book.checkCapsule(r.companionId(), 0L));
        book.live(r.withOwner(UUID.randomUUID()), UUID.randomUUID(), HERE, 10L, 0L);
        assertFalse(book.isTombstoned(r.companionId()));
    }

    @Test
    void aLedgerRestoredFromAnOlderBackupAdoptsInsteadOfUnbinding() {
        RecoveryBook backup = book();
        CompanionRecord old = record(owner);
        backup.live(old, UUID.randomUUID(), HERE, 0L, 0L);
        RecoveryBook restored = restart(backup);
        // Deployed after the backup was taken: the restored ledger has never heard of it.
        CompanionRecord newer = record(owner);
        UUID entity = UUID.randomUUID();
        assertEquals(RecoveryBook.Verdict.ADOPT, restored.verdict(newer.companionId(), entity, owner,
                RecoveryBook.BayProbe.UNKNOWN), "no entry and no tombstone: adopted, never unbound");
        restored.live(newer, entity, HERE, 0L, 0L);
        assertEquals(2, restored.liveFor(owner, null), "adopted companions count toward the cap from then on");
    }

    @Test
    void aPurgedLostEntryAdoptsTheEntityWhenItFinallyLoads() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID entity = UUID.randomUUID();
        book.live(r, entity, HERE, 0L, 0L);
        book.sweepLost(LOST_TICKS + 1, LOST_TICKS, o -> true, 0L);
        long day = 24L * 60L * 60L * 1000L;
        assertEquals(1, book.purgeUntouched(181 * day, 180 * day));
        assertEquals(RecoveryBook.Verdict.ADOPT, book.verdict(r.companionId(), entity, owner,
                RecoveryBook.BayProbe.UNKNOWN));
    }

    @Test
    void aPurgedCapsuleEntryPlusAStaleEntityCannotDoubleTheCompanion() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID oldEntity = UUID.randomUUID();
        book.live(r, oldEntity, HERE, 0L, 0L);
        book.sweepLost(LOST_TICKS + 1, LOST_TICKS, o -> true, 0L);
        long ticket = book.reclaim(owner, r.companionId(), 100L, 0L).ticket();
        long day = 24L * 60L * 60L * 1000L;
        book.purgeUntouched(181 * day, 180 * day);
        // The old entity finally loads: adopted under its owner (not an ownerless Capsule)...
        assertEquals(RecoveryBook.Verdict.ADOPT, book.verdict(r.companionId(), oldEntity, owner,
                RecoveryBook.BayProbe.UNKNOWN));
        book.live(r, oldEntity, HERE, 200L, 0L);
        // ...and the reclaimed Capsule can no longer bring out a second copy.
        assertEquals(RecoveryBook.ALREADY_OUT, book.checkCapsule(r.companionId(), ticket));
    }

    @Test
    void tombstonesCarryNoPersonAndFollowRetention() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        book.forgetOwner(owner, 0L);
        assertEquals(java.util.Map.of(r.companionId(), 0L), book.tombstones(), "id and time only");
        long day = 24L * 60L * 60L * 1000L;
        assertEquals(1, book.purgeUntouched(181 * day, 180 * day));
        assertFalse(book.isTombstoned(r.companionId()));
    }

    // =================================================================================================
    // Defence in depth: recall, park and Bay hand-out check the state themselves
    // =================================================================================================

    @Test
    void recallParkAndHandOutRefuseAStaleCopy() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        UUID real = UUID.randomUUID();
        book.live(r, real, HERE, 0L, 0L);
        assertEquals(0L, book.recall(r, UUID.randomUUID(), 1L, 0L), "a stale copy cannot be recalled");
        assertFalse(book.park(r, UUID.randomUUID(), BAY, 1L, 0L), "nor parked");
        assertEquals(State.LIVE, book.get(r.companionId()).orElseThrow().state(), "nothing changed");
        assertTrue(book.park(r, real, BAY, 2L, 0L));
        HomePos otherBay = new HomePos(OVERWORLD, new BlockPos(50, 64, 50));
        assertEquals(RecoveryBook.HandOut.REFUSE, book.handOut(r, otherBay, 3L, 0L).action());
        RecoveryBook.HandOutResult out = book.handOut(r, BAY, 4L, 0L);
        assertEquals(RecoveryBook.HandOut.CAPSULE, out.action());
        assertNull(book.checkCapsule(r.companionId(), out.ticket()));
        assertEquals(RecoveryBook.HandOut.REFUSE, book.handOut(r, BAY, 5L, 0L).action(), "never twice");
    }

    @Test
    void aBayBrokenBeforeItsFirstTickUnbindsAnErasedOwnersRecord() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.parked(r, BAY, 0L, 0L);
        book.forgetOwner(owner, 0L);
        assertEquals(RecoveryBook.HandOut.UNBIND, book.handOut(r, BAY, 1L, 0L).action());
        assertFalse(book.holdsFor(owner), "no ledger row is re-minted for the erased owner");
    }

    // =================================================================================================
    // Departed owners and stranded Bays
    // =================================================================================================

    @Test
    void liveRowsOfAnOwnerWhoNeverReturnsAgeToLostAndDropTheirPosition() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        long day = 24L * 60L * 60L * 1000L;
        book.live(r, UUID.randomUUID(), HERE, 0L, 0L);
        assertTrue(book.sweepLost(10L, LOST_TICKS, o -> false, 29 * day, 30 * day).isEmpty());
        List<LedgerEntry> lost = book.sweepLost(20L, LOST_TICKS, o -> false, 31 * day, 30 * day);
        assertEquals(1, lost.size(), "aged out although the owner never came back");
        LedgerEntry entry = book.get(r.companionId()).orElseThrow();
        assertEquals(State.LOST, entry.state());
        assertTrue(entry.lastKnown().isEmpty(), "its last-known position is dropped");
        assertEquals(1, book.purgeUntouched(31 * day + 181 * day, 180 * day), "then retention deletes it");
    }

    @Test
    void theRegionCapCountsOnlyRecentlySeenCompanions() {
        RecoveryBook book = book();
        CompanionLimits limits = new CompanionLimits(100, 2, 32, 64, 10, 96);
        RegionKey region = RegionKey.ofBlock(OVERWORLD.toString(), HERE.pos().getX(), HERE.pos().getZ());
        // Two departed players' companions, last seen at tick 0, chunks unloaded since.
        book.live(record(UUID.randomUUID()), UUID.randomUUID(), HERE, 0L, 0L);
        book.live(record(UUID.randomUUID()), UUID.randomUUID(), HERE, 0L, 0L);
        assertEquals("authority.nerocompanion.region_cap",
                CapPolicy.canDeploy(book.counter(100L, LOST_TICKS), owner, UUID.randomUUID(), region, limits)
                        .reasonKey(), "recently seen: they count");
        assertTrue(CapPolicy.canDeploy(book.counter(LOST_TICKS + 10_000L, LOST_TICKS), owner, UUID.randomUUID(),
                region, limits).allowed(), "ghosts no longer fill the region");
    }

    @Test
    void aParkedCompanionWhoseBayNoLongerHoldsItIsFoundOrLostNeverStuck() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.parked(r, BAY, 0L, 0L);
        UUID entity = UUID.randomUUID();
        RecoveryBook.BayProbe empty = (bay, id) -> RecoveryBook.BayState.MISSING;
        RecoveryBook.BayProbe holds = (bay, id) -> RecoveryBook.BayState.HOLDS;
        assertEquals(RecoveryBook.Verdict.STALE, book.verdict(r.companionId(), entity, owner, holds),
                "the Bay holds it: this entity is a copy");
        assertEquals(RecoveryBook.Verdict.STALE, book.verdict(r.companionId(), entity, owner,
                RecoveryBook.BayProbe.UNKNOWN), "Bay not loaded: wait");
        assertEquals(RecoveryBook.Verdict.FOUND, book.verdict(r.companionId(), entity, owner, empty),
                "an entity present and a loaded Bay without it: adopt the entity");
        assertTrue(book.sweepOrphanedParked(holds, 1L, 0L).isEmpty());
        assertEquals(1, book.sweepOrphanedParked(empty, 2L, 0L).size(), "no entity: LOST, reclaimable");
        assertTrue(book.reclaim(owner, r.companionId(), 3L, 0L).ok());
    }

    @Test
    void anOperatorReleaseMakesAnyStuckEntryLost() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.parked(r, BAY, 0L, 0L);
        assertTrue(book.release(r.companionId(), 1L, 0L));
        assertEquals(State.LOST, book.get(r.companionId()).orElseThrow().state());
        assertFalse(book.release(r.companionId(), 2L, 0L), "already lost");
        assertFalse(book.release(UUID.randomUUID(), 2L, 0L));
    }

    // =================================================================================================
    // Caps across a restart
    // =================================================================================================

    @Test
    void capsComeFromTheLedgerAndSurviveARestart() {
        RecoveryBook book = book();
        CompanionLimits limits = new CompanionLimits(2, 3, 32, 64, 10, 96);
        RegionKey region = RegionKey.ofBlock(OVERWORLD.toString(), HERE.pos().getX(), HERE.pos().getZ());
        book.live(record(owner), UUID.randomUUID(), HERE, 0L, 0L);
        book.live(record(owner), UUID.randomUUID(), HERE, 0L, 0L);
        RecoveryBook after = restart(book);
        assertEquals("authority.nerocompanion.player_cap",
                CapPolicy.canDeploy(after, owner, UUID.randomUUID(), region, limits).reasonKey(),
                "the player cap holds after a restart, before any chunk loads");
        UUID other = UUID.randomUUID();
        after.live(record(other), UUID.randomUUID(), HERE, 0L, 0L);
        assertEquals("authority.nerocompanion.region_cap",
                CapPolicy.canDeploy(after, UUID.randomUUID(), UUID.randomUUID(), region, limits).reasonKey());
        RegionKey elsewhere = RegionKey.ofBlock(OVERWORLD.toString(), 1_000, 1_000);
        assertTrue(CapPolicy.canDeploy(after, other, UUID.randomUUID(), elsewhere, limits).allowed());
    }

    @Test
    void lostAndCapsuledCompanionsDoNotCount() {
        RecoveryBook book = book();
        CompanionRecord a = record(owner);
        CompanionRecord b = record(owner);
        book.live(a, UUID.randomUUID(), HERE, 0L, 0L);
        book.live(b, UUID.randomUUID(), HERE, 0L, 0L);
        assertEquals(2, book.liveFor(owner, null));
        book.toCapsule(a, 10L, 0L);
        book.sweepLost(LOST_TICKS + 1, LOST_TICKS, o -> true, 0L);
        assertEquals(0, book.liveFor(owner, null));
        assertFalse(book.isLiveElsewhere(a.companionId(), null));
    }

    // =================================================================================================
    // Retention and erasure
    // =================================================================================================

    @Test
    void retentionPurgesOnlyOldCapsuleAndLostEntries() {
        RecoveryBook book = book();
        CompanionRecord live = record(owner);
        CompanionRecord parked = record(owner);
        CompanionRecord capsule = record(owner);
        CompanionRecord lost = record(owner);
        book.live(live, UUID.randomUUID(), HERE, 0L, 0L);
        book.parked(parked, BAY, 0L, 0L);
        book.toCapsule(capsule, 0L, 0L);
        UUID lostEntity = UUID.randomUUID();
        book.live(lost, lostEntity, HERE, 0L, 0L);
        book.died(lost, lostEntity, HERE, 0L, 0L);
        long day = 24L * 60L * 60L * 1000L;
        assertEquals(0, book.purgeUntouched(179 * day, 180 * day));
        assertEquals(2, book.purgeUntouched(181 * day, 180 * day));
        assertEquals(Set.of(live.companionId(), parked.companionId()),
                Set.copyOf(book.all().stream().map(LedgerEntry::companionId).toList()));
    }

    @Test
    void forgetOwnerRemovesEveryEntryOfThatOwnerOnly() {
        RecoveryBook book = book();
        UUID other = UUID.randomUUID();
        book.live(record(owner), UUID.randomUUID(), HERE, 0L, 0L);
        book.toCapsule(record(owner), 0L, 0L);
        book.live(record(other), UUID.randomUUID(), HERE, 0L, 0L);
        assertEquals(2, book.forgetOwner(owner));
        assertFalse(book.holdsFor(owner));
        assertTrue(book.holdsFor(other));
        assertEquals(0, book.forgetOwner(UUID.randomUUID()), "an unknown player is tolerated");
    }

    @Test
    void entriesRoundTripThroughTheCodec() {
        RecoveryBook book = book();
        CompanionRecord r = record(owner);
        book.parked(r, BAY, 7L, 99L);
        LedgerEntry before = book.get(r.companionId()).orElseThrow();
        LedgerEntry after = restart(book).get(r.companionId()).orElseThrow();
        assertEquals(before.state(), after.state());
        assertEquals(before.bay(), after.bay());
        assertEquals(before.lastKnown(), after.lastKnown());
        assertEquals(before.touchedAt(), after.touchedAt());
        assertEquals(before.snapshot().name(), after.snapshot().name());
    }
}
