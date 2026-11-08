package za.co.neroland.nerocompanion.ai.drone;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;

/** Carry-lite takes only what the owner could pick up, within 4 blocks, in the right mode. */
class PickupRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void ownerOrUnthrownItemsInReach() {
        assertTrue(PickupRules.mayPickUp(true, false, null, OWNER, 1.0D, false));
        assertTrue(PickupRules.mayPickUp(true, false, OWNER, OWNER, 16.0D, false));
    }

    @Test
    void neverAnotherPlayersThrowOrADelayedItem() {
        assertFalse(PickupRules.mayPickUp(true, false, OTHER, OWNER, 1.0D, false));
        assertFalse(PickupRules.mayPickUp(true, true, OWNER, OWNER, 1.0D, false));
        assertFalse(PickupRules.mayPickUp(true, true, null, OWNER, 1.0D, false));
    }

    @Test
    void notBeyondFourBlocksNotDeadNotOwnerless() {
        assertFalse(PickupRules.mayPickUp(true, false, null, OWNER, 16.01D, false));
        assertFalse(PickupRules.mayPickUp(false, false, null, OWNER, 1.0D, false));
        assertFalse(PickupRules.mayPickUp(true, false, null, null, 1.0D, false));
    }

    @Test
    void modeRules() {
        assertTrue(PickupRules.collects(CompanionMode.CARRY, true));
        assertTrue(PickupRules.collects(CompanionMode.CARRY, false));
        assertTrue(PickupRules.collects(CompanionMode.FOLLOW, false));
        assertFalse(PickupRules.collects(CompanionMode.FOLLOW, true));
        assertFalse(PickupRules.collects(CompanionMode.STAY, false));
        assertFalse(PickupRules.collects(CompanionMode.SCOUT, false));
        assertTrue(PickupRules.handsBack(CompanionMode.FOLLOW, true, 9.0D));
        assertFalse(PickupRules.handsBack(CompanionMode.FOLLOW, true, 25.0D));
        assertFalse(PickupRules.handsBack(CompanionMode.FOLLOW, false, 1.0D));
        assertFalse(PickupRules.handsBack(CompanionMode.CARRY, true, 1.0D));
        assertTrue(PickupRules.CARGO_SLOTS == 8);
    }

    @Test
    void ownerReachAndGiveUp() {
        assertTrue(PickupRules.withinOwnerReach(49.0D, 3.0D));
        assertFalse(PickupRules.withinOwnerReach(49.1D, 3.0D));
        assertFalse(PickupRules.giveUp(10, true, false));
        assertTrue(PickupRules.giveUp(40, true, false), "navigator stalled");
        assertFalse(PickupRules.giveUp(60, false, false));
        assertTrue(PickupRules.giveUp(100, false, false));
        assertFalse(PickupRules.giveUp(100, true, true));
    }

    @Test
    void aFailedItemIsSkippedForTheBackOff() {
        RetryMemory memory = new RetryMemory();
        UUID item = UUID.randomUUID();
        memory.failed(item, 1000L);
        assertTrue(memory.blocked(item, 1000L));
        assertTrue(memory.blocked(item, 1599L));
        assertFalse(memory.blocked(item, 1600L));
        assertFalse(memory.blocked(UUID.randomUUID(), 1000L));
        memory.prune(1600L);
        assertTrue(memory.size() == 0);
        for (int i = 0; i < 100; i++) {
            memory.failed(UUID.randomUUID(), 2000L + i);
        }
        assertTrue(memory.size() <= RetryMemory.MAX_ENTRIES);
    }
}
