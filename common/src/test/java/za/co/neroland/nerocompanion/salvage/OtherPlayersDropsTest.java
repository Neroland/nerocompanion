package za.co.neroland.nerocompanion.salvage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.drone.PickupRules;
import za.co.neroland.nerocompanion.carry.CarryRules;

/**
 * Two players: Bob dies near Alice. Alice's carry bot (CARRY) and Alice's personal drone (carry-lite) must leave
 * Bob's death drops alone while Bob's window is open, even with salvage turned off; after the window closes the
 * ordinary pickup rules apply again. Bob's own companions are not blocked by Bob's window.
 */
class OtherPlayersDropsTest {

    private static final String OW = "minecraft:overworld";
    private static final long DEATH = 1_000L;
    private static final long WINDOW = 1_200L;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private DeathWindows bobDiesWith(DeathWindows.Candidate drop) {
        DeathWindows windows = new DeathWindows();
        // Salvage off in the config still opens the window for marking (only gathering is gated).
        assertTrue(SalvageRules.opensWindow(false));
        DeathWindows.Window window = windows.open(this.bob, OW, 10, 64, 10, DEATH, WINDOW);
        windows.releaseContested(window, List.of(drop), DEATH);
        windows.claim(window, List.of(drop), DEATH);
        return windows;
    }

    private static DeathWindows.Candidate drop() {
        return new DeathWindows.Candidate(UUID.randomUUID(), OW, 10.2D, 65.3D, 9.8D, 0, false, 32);
    }

    /** Alice's carry bot, as {@code CarryGoal.mayTake} asks (death drops have no thrower). */
    private boolean carryBotTakes(DeathWindows windows, UUID owner, UUID item, long now) {
        return CarryRules.mayTake(true, false, false, null, owner, windows.isClaimedByOther(owner, item, now));
    }

    /** Alice's drone, as {@code CarryLiteGoal.allowed} asks. */
    private boolean droneTakes(DeathWindows windows, UUID owner, UUID item, long now) {
        return PickupRules.mayPickUp(true, false, null, owner, 1.0D, windows.isClaimedByOther(owner, item, now));
    }

    @Test
    void aliceCarryBotLeavesBobsDeathPileForTheWholeWindow() {
        DeathWindows.Candidate pile = drop();
        DeathWindows windows = this.bobDiesWith(pile);
        for (long t = DEATH + 40L; t <= DEATH + WINDOW; t += 40L) {
            assertFalse(this.carryBotTakes(windows, this.alice, pile.itemId(), t), "tick " + t);
        }
        assertTrue(this.carryBotTakes(windows, this.alice, pile.itemId(), DEATH + WINDOW + 1L),
                "after the window, vanilla rules apply");
        windows.expire(DEATH + WINDOW + 1L);
        assertTrue(this.carryBotTakes(windows, this.alice, pile.itemId(), DEATH + WINDOW + 1L));
    }

    @Test
    void aliceDroneLeavesBobsDeathPileForTheWholeWindow() {
        DeathWindows.Candidate pile = drop();
        DeathWindows windows = this.bobDiesWith(pile);
        for (long t = DEATH + 40L; t <= DEATH + WINDOW; t += 40L) {
            assertFalse(this.droneTakes(windows, this.alice, pile.itemId(), t), "tick " + t);
        }
        assertTrue(this.droneTakes(windows, this.alice, pile.itemId(), DEATH + WINDOW + 1L),
                "after the window, vanilla rules apply");
    }

    @Test
    void bobsOwnCompanionsAndUnclaimedItemsAreNotBlocked() {
        DeathWindows.Candidate pile = drop();
        DeathWindows windows = this.bobDiesWith(pile);
        assertTrue(this.carryBotTakes(windows, this.bob, pile.itemId(), DEATH + 40L), "Bob's own bot");
        assertTrue(this.droneTakes(windows, this.bob, pile.itemId(), DEATH + 40L), "Bob's own drone");
        UUID unrelated = UUID.randomUUID();
        assertTrue(this.carryBotTakes(windows, this.alice, unrelated, DEATH + 40L), "an item nobody died with");
        assertTrue(this.droneTakes(windows, this.alice, unrelated, DEATH + 40L));
    }

    @Test
    void aGatheredOrErasedDropIsNoLongerProtected() {
        DeathWindows.Candidate pile = drop();
        DeathWindows windows = this.bobDiesWith(pile);
        windows.release(this.bob, pile.itemId());
        assertFalse(windows.isClaimedByOther(this.alice, pile.itemId(), DEATH + 40L));
        DeathWindows.Candidate other = drop();
        DeathWindows again = this.bobDiesWith(other);
        again.forget(this.bob);
        assertFalse(again.isClaimedByOther(this.alice, other.itemId(), DEATH + 40L));
    }

    @Test
    void thrownItemsKeepTheirOldRule() {
        assertFalse(CarryRules.mayTake(true, false, false, this.bob, this.alice, false), "Bob threw it");
        assertTrue(CarryRules.mayTake(true, false, false, this.alice, this.alice, false), "Alice threw it");
        assertFalse(CarryRules.mayTake(true, true, false, null, this.alice, false), "pickup delay");
        assertFalse(CarryRules.mayTake(true, false, true, null, this.alice, false), "empty");
    }
}
