package za.co.neroland.nerocompanion.carry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.Decision;

/** Binding and depositing are gated by mayOpen, at bind time and at every deposit. */
class DepositRulesTest {

    @Test
    void aDeniedDepositMovesNothingAndNeverTouchesTheContainer() {
        AtomicInteger calls = new AtomicInteger();
        DepositRules.Result<String> result = DepositRules.deposit(Decision.deny("locked"), List.of("cobble", "", "iron"),
                stack -> {
                    calls.incrementAndGet();
                    return "";
                }, String::length);
        assertTrue(result.denied());
        assertEquals("authority.nerocompanion.locked", result.reasonKey());
        assertEquals(0, calls.get());
        assertEquals(List.of("cobble", "", "iron"), result.remaining());
        assertEquals(0, result.moved());
    }

    @Test
    void anAllowedDepositOffersEveryNonEmptySlot() {
        AtomicInteger calls = new AtomicInteger();
        DepositRules.Result<String> result = DepositRules.deposit(Decision.ALLOW, List.of("cobble", "", "iron"),
                stack -> {
                    calls.incrementAndGet();
                    return stack.equals("iron") ? "iron" : "";
                }, String::length);
        assertFalse(result.denied());
        assertEquals(2, calls.get());
        assertEquals(List.of("", "", "iron"), result.remaining());
        assertEquals(6, result.moved()); // counted by size: "cobble" went in, "iron" did not
        assertTrue(result.movedAny());
    }

    /** A mutable stack: the insert hands back the same object, emptied or not. */
    private static final class Pile {
        int count;

        Pile(int count) {
            this.count = count;
        }
    }

    @Test
    void movedIsCountedByItemsNotByIdentity() {
        Pile full = new Pile(64);
        DepositRules.Result<Pile> nothing = DepositRules.deposit(Decision.ALLOW, List.of(full), pile -> pile,
                pile -> pile.count);
        assertEquals(0, nothing.moved());
        assertFalse(nothing.movedAny());
        DepositRules.Result<Pile> some = DepositRules.deposit(Decision.ALLOW, List.of(new Pile(64)), pile -> {
            pile.count -= 10;
            return pile;
        }, pile -> pile.count);
        assertEquals(10, some.moved());
    }

    @Test
    void aContainerThatThrowsKeepsTheRestAboard() {
        DepositRules.Result<String> result = DepositRules.deposit(Decision.ALLOW, List.of("aa", "bb", "cc"),
                stack -> {
                    if (stack.equals("bb")) {
                        throw new IllegalStateException("modded container");
                    }
                    return "";
                }, String::length);
        assertTrue(result.failed());
        assertEquals(List.of("", "bb", "cc"), result.remaining());
        assertEquals(2, result.moved());
    }

    @Test
    void bindingNeedsAContainerAndMayOpen() {
        assertEquals("authority.nerocompanion.not_container", DepositRules.bind(false, Decision.ALLOW).reasonKey());
        assertTrue(DepositRules.bind(true, Decision.deny("provider")).denied());
        assertTrue(DepositRules.bind(true, Decision.ALLOW).allowed());
    }
}
