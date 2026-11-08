package za.co.neroland.nerocompanion.carry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Carry triggers: auto-deposit at 80 %, owner requests, pickup radius bounds. */
class CarryRulesTest {

    @Test
    void autoDepositStartsAtEightyPercentOnlyWithTheChip() {
        assertFalse(CarryRules.shouldDeposit(21, 27, true, false));
        assertTrue(CarryRules.shouldDeposit(22, 27, true, false));
        assertFalse(CarryRules.shouldDeposit(26, 27, false, false));
        assertTrue(CarryRules.shouldDeposit(1, 27, false, true));
        assertFalse(CarryRules.shouldDeposit(0, 27, true, true));
    }

    @Test
    void pickupRadiusAddsTheRangeChipAndIsCapped() {
        assertEquals(8, CarryRules.pickupRadius(0, 0));
        assertEquals(14, CarryRules.pickupRadius(8, 6));
        assertEquals(CarryRules.MAX_PICKUP_RADIUS, CarryRules.pickupRadius(15, 6));
    }

    @Test
    void aFullBotStopsPickingUp() {
        assertTrue(CarryRules.mayPickUp(26, 27));
        assertFalse(CarryRules.mayPickUp(27, 27));
        assertFalse(CarryRules.mayPickUp(0, 0));
    }
}
