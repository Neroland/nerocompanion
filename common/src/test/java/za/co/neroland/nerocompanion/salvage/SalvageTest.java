package za.co.neroland.nerocompanion.salvage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Death salvage as pure models: a window claims only the owner's own death drops - never another
 * player's (two players dying apart, together, and a tick apart), never thrown or old items; the keepInventory and
 * config guards; the hold's one-per-owner, cap, return, expiry, retention and erasure rules.
 */
class SalvageTest {

    private static final String OW = "minecraft:overworld";

    private static DeathWindows.Candidate drop(double x, double z) {
        return drop(x, z, 0, 16);
    }

    private static DeathWindows.Candidate drop(double x, double z, int age, int count) {
        return new DeathWindows.Candidate(UUID.randomUUID(), OW, x, 65.3D, z, age, false, count);
    }

    // =================================================================================================
    // Which items are the owner's death drops
    // =================================================================================================

    @Test
    void twoPlayersDyingApartEachGetOnlyTheirOwnDrops() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<DeathWindows.Candidate> aliceDrops = List.of(drop(0.3, 0.1), drop(-0.6, 0.5), drop(0.4, -0.6));
        List<DeathWindows.Candidate> bobDrops = List.of(drop(20.2, 0.0), drop(20.9, 0.8));
        List<DeathWindows.Candidate> everything = new ArrayList<>(aliceDrops);
        everything.addAll(bobDrops);

        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        windows.releaseContested(a, everything, 100L);
        assertEquals(3, windows.claim(a, everything, 100L));
        DeathWindows.Window b = windows.open(bob, OW, 20.5, 64, 0.5, 100L, 1200L);
        windows.releaseContested(b, everything, 100L);
        assertEquals(2, windows.claim(b, everything, 100L));

        for (DeathWindows.Candidate c : aliceDrops) {
            assertTrue(windows.isOwners(alice, c.itemId()));
            assertFalse(windows.isOwners(bob, c.itemId()), "never another player's drops");
        }
        for (DeathWindows.Candidate c : bobDrops) {
            assertTrue(windows.isOwners(bob, c.itemId()));
            assertFalse(windows.isOwners(alice, c.itemId()));
        }
    }

    @Test
    void twoPlayersDyingAtTheSameSpotAndMomentGetNeither() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<DeathWindows.Candidate> pile = List.of(drop(0.2, 0.2), drop(0.6, -0.4), drop(-0.5, 0.5));
        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        DeathWindows.Window b = windows.open(bob, OW, 0.5, 64, 0.5, 100L, 1200L);
        windows.releaseContested(a, pile, 100L);
        assertEquals(0, windows.claim(a, pile, 100L), "ambiguous: both deaths cover them");
        windows.releaseContested(b, pile, 100L);
        assertEquals(0, windows.claim(b, pile, 100L));
    }

    @Test
    void aSecondDeathATickLaterReleasesTheFirstClaimOnSharedItems() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<DeathWindows.Candidate> aliceDrops = List.of(drop(0.2, 0.2));
        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        windows.releaseContested(a, aliceDrops, 100L);
        assertEquals(1, windows.claim(a, aliceDrops, 100L));
        // Bob dies on the same spot one tick later: Alice's (now 1 tick old) items and Bob's new ones overlap.
        DeathWindows.Candidate aliceAged = new DeathWindows.Candidate(aliceDrops.get(0).itemId(), OW, 0.4, 65.2, 0.2,
                1, false, 16);
        DeathWindows.Candidate bobs = drop(0.6, -0.3);
        List<DeathWindows.Candidate> both = List.of(aliceAged, bobs);
        DeathWindows.Window b = windows.open(bob, OW, 0.5, 64, 0, 101L, 1200L);
        windows.releaseContested(b, both, 101L);
        windows.claim(b, both, 101L);
        assertFalse(windows.isOwners(alice, aliceAged.itemId()), "contested: released");
        assertFalse(windows.isOwners(bob, aliceAged.itemId()), "and never handed to the other player");
        assertFalse(windows.isOwners(bob, bobs.itemId()), "Bob's own are ambiguous too (Alice's death is recent)");
    }

    @Test
    void aMergedForeignStackYieldsOnlyTheClaimedCount() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        DeathWindows.Candidate cobble = drop(0.1, 0.1, 0, 30);
        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        windows.claim(a, List.of(cobble), 100L);
        assertEquals(30, windows.claimedCount(alice, cobble.itemId()));
        // Vanilla merges another player's 30 cobblestone into the claimed entity: 60 lie there now.
        assertEquals(30, DeathWindows.takeCount(windows.claimedCount(alice, cobble.itemId()), 60),
                "the other player's 30 stay on the ground");
        // The claimed stack merged away into another entity: only what is left is taken.
        assertEquals(12, DeathWindows.takeCount(30, 12));
        assertEquals(0, DeathWindows.takeCount(windows.claimedCount(alice, UUID.randomUUID()), 64),
                "an unclaimed entity yields nothing");
    }

    @Test
    void nearbyForeignDropsThrownOldAndFarItemsAreNotDeathDrops() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        DeathWindows.Candidate thrown = new DeathWindows.Candidate(UUID.randomUUID(), OW, 0, 65.3, 0, 0, true, 1);
        DeathWindows.Candidate old = drop(0, 0, DeathWindows.MAX_AGE + 1, 1);
        DeathWindows.Candidate blockDropTwoAway = drop(2.0, 0, 0, 1);
        DeathWindows.Candidate mobLootBelow = new DeathWindows.Candidate(UUID.randomUUID(), OW, 0.2, 62.0, 0.1, 0,
                false, 1);
        DeathWindows.Candidate otherDimension = new DeathWindows.Candidate(UUID.randomUUID(), "minecraft:the_nether",
                0, 65.3, 0, 0, false, 1);
        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        assertEquals(0, windows.claim(a, List.of(thrown, old, blockDropTwoAway, mobLootBelow, otherDimension), 100L));
    }

    @Test
    void aSecondDeathInsideTheWindowAddsToItAndKeepsTheFirstDrops() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        DeathWindows.Candidate first = drop(0.2, 0.2);
        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        windows.claim(a, List.of(first), 100L);
        DeathWindows.Candidate second = drop(30.1, 0.3);
        DeathWindows.Window again = windows.open(alice, OW, 30, 64, 0, 500L, 1200L);
        windows.claim(again, List.of(second), 500L);
        assertTrue(windows.isOwners(alice, first.itemId()), "the first death's drops are still targets");
        assertTrue(windows.isOwners(alice, second.itemId()));
        assertEquals(1700L, windows.active(alice, 1700L).orElseThrow().expiresAt());
        assertTrue(again.near(OW, 29, 64, 0, 24) && again.near(OW, 1, 64, 0, 24), "near either death");
    }

    @Test
    void windowsExpireAndAreForgotten() {
        DeathWindows windows = new DeathWindows();
        UUID alice = UUID.randomUUID();
        DeathWindows.Window a = windows.open(alice, OW, 0, 64, 0, 100L, 1200L);
        windows.claim(a, List.of(drop(0, 0)), 100L);
        assertTrue(windows.active(alice, 1300L).isPresent());
        assertTrue(windows.active(alice, 1301L).isEmpty(), "past salvageWindowTicks");
        windows.expire(1301L);
        assertFalse(windows.holds(alice));
        windows.open(alice, OW, 0, 64, 0, 2000L, 1200L);
        windows.forget(alice);
        assertFalse(windows.holds(alice), "erasure forgets the window");
    }

    @Test
    void keepInventoryOpensNoWindowButConfigOffStillMarks() {
        assertTrue(SalvageRules.opensWindow(false));
        assertFalse(SalvageRules.opensWindow(true), "never with keepInventory");
    }

    @Test
    void whoGathers() {
        assertTrue(SalvageRules.gathers(true, true, true, false), "a carry bot by default");
        assertFalse(SalvageRules.gathers(true, false, true, false), "carry bots need the chip when the default is off");
        assertTrue(SalvageRules.gathers(true, false, false, true), "anyone with the Salvage Chip");
        assertFalse(SalvageRules.gathers(true, true, false, false), "others without the chip do not");
        assertFalse(SalvageRules.gathers(false, true, true, true), "nobody when salvage is off");
        assertFalse(SalvageRules.keepsAwake(false, true, true), "an offline owner's companions sleep");
        assertTrue(SalvageRules.keepsAwake(true, true, true));
    }

    // =================================================================================================
    // The hold
    // =================================================================================================

    @Test
    void oneHoldPerOwnerReturnedByAnyHolderOnce() {
        HoldBook<String> hold = new HoldBook<>();
        UUID owner = UUID.randomUUID();
        UUID carryBot = UUID.randomUUID();
        UUID drone = UUID.randomUUID();
        assertTrue(hold.add(owner, carryBot, "pickaxe", 100L, 72_000L, 0L));
        assertTrue(hold.add(owner, drone, "torches", 200L, 72_000L, 0L));
        assertEquals(72_100L, hold.get(owner).orElseThrow().expiresAt(), "the first item fixes the expiry");
        assertTrue(hold.returnTo(owner, UUID.randomUUID()).isEmpty(), "a companion that gathered nothing has none");
        assertTrue(hold.returnTo(UUID.randomUUID(), carryBot).isEmpty(), "never to another player");
        assertEquals(List.of("pickaxe", "torches"), hold.returnTo(owner, drone));
        assertTrue(hold.returnTo(owner, carryBot).isEmpty(), "returned once");
    }

    @Test
    void aFullHoldTakesNothingMore() {
        HoldBook<Integer> hold = new HoldBook<>();
        UUID owner = UUID.randomUUID();
        UUID bot = UUID.randomUUID();
        for (int i = 0; i < HoldBook.MAX_STACKS; i++) {
            assertTrue(hold.add(owner, bot, i, 0L, 100L, 0L));
        }
        assertTrue(hold.isFull(owner));
        assertFalse(hold.add(owner, bot, 999, 0L, 100L, 0L));
        assertEquals(HoldBook.MAX_STACKS, hold.get(owner).orElseThrow().items().size());
    }

    @Test
    void anExpiredHoldDropsAtItsHolderOnlyAfterExpiry() {
        HoldBook<String> hold = new HoldBook<>();
        UUID owner = UUID.randomUUID();
        UUID bot = UUID.randomUUID();
        hold.add(owner, bot, "sword", 0L, 1_000L, 0L);
        assertTrue(hold.takeExpired(owner, bot, 1_000L).isEmpty(), "not yet");
        assertTrue(hold.takeExpired(owner, UUID.randomUUID(), 2_000L).isEmpty(), "only a holder drops it");
        assertEquals(List.of("sword"), hold.takeExpired(owner, bot, 1_001L));
        assertFalse(hold.holdsFor(owner));
    }

    @Test
    void retentionAndErasureClearHolds() {
        HoldBook<String> hold = new HoldBook<>();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        hold.add(owner, UUID.randomUUID(), "a", 0L, 10L, 0L);
        hold.add(other, UUID.randomUUID(), "b", 0L, 10L, 5_000L);
        assertEquals(1, hold.purgeUntouched(6_000L, 5_500L));
        assertFalse(hold.holdsFor(owner));
        assertTrue(hold.forgetOwner(other));
        assertEquals(Set.of(), hold.all().keySet());
    }
}
