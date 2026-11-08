package za.co.neroland.nerocompanion.pet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.pet.FetchRules;

/** A pet fetches only its own owner's ball. */
class FetchRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void onlyTheOwnersBallIsFetched() {
        assertTrue(FetchRules.mayFetch(OWNER, OWNER));
        assertTrue(FetchRules.mayFetch(OWNER, UUID.fromString(OWNER.toString())));
        assertFalse(FetchRules.mayFetch(OWNER, STRANGER));
        assertFalse(FetchRules.mayFetch(OWNER, null), "a ball with no recorded thrower is nobody's");
        assertFalse(FetchRules.mayFetch(null, OWNER), "an ownerless pet fetches nothing");
        assertFalse(FetchRules.mayFetch(null, null));
    }

    @Test
    void reachAndTimeoutBounds() {
        assertTrue(FetchRules.withinPickup(1.5D * 1.5D));
        assertFalse(FetchRules.withinPickup(1.6D * 1.6D));
        assertTrue(FetchRules.withinDelivery(4.0D));
        assertFalse(FetchRules.withinDelivery(4.1D));
        assertTrue(FetchRules.withinReturn(24.0D * 24.0D));
        assertFalse(FetchRules.withinReturn(25.0D * 25.0D));
        assertFalse(FetchRules.seekTimedOut(100L, 100L + FetchRules.SEEK_TIMEOUT - 1));
        assertTrue(FetchRules.seekTimedOut(100L, 100L + FetchRules.SEEK_TIMEOUT));
        assertTrue(FetchRules.SCAN_INTERVAL >= 10, "ball scans run on a cadence, never every tick");
    }
}
