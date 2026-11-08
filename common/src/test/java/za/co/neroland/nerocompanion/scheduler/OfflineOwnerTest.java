package za.co.neroland.nerocompanion.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.charge.BaySlots;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.salvage.SalvageRules;

/**
 * Multiplayer rules: an offline owner's companions sleep - working, salvaging or not - so they never mine, deposit or
 * attack (no goals run in SLEEP and no work goal is selected for it); logging in wakes them at the next evaluation.
 * A Bay record can only be unparked by its own owner.
 */
class OfflineOwnerTest {

    private static final CompanionLimits LIMITS = CompanionLimits.DEFAULTS;

    private static CompanionScheduler.Input input(boolean online, boolean working, boolean salvaging) {
        return new CompanionScheduler.Input(true, online, true, 4.0D, working, false, false, true, salvaging);
    }

    @Test
    void logoutSleepsEveryCompanionWhateverItWasDoing() {
        for (boolean working : new boolean[] {false, true}) {
            for (boolean salvaging : new boolean[] {false, true}) {
                Throttle t = CompanionScheduler.evaluate(input(false, working, salvaging), LIMITS);
                assertTrue(t.sleeping(), "offline owner: asleep (working=" + working + ", salvaging=" + salvaging + ")");
                assertEquals(Throttle.Reason.OWNER_OFFLINE, t.reason());
                assertFalse(CompanionScheduler.stepsAi(t.state(), 0, 0), "no AI step, so no goal runs");
            }
        }
        assertFalse(SalvageRules.keepsAwake(false, true, true));
    }

    @Test
    void noWorkGoalIsSelectedWhileAsleep() {
        List<Identifier> work = List.of(Identifier.fromNamespaceAndPath("nerocompanion", "mine_job"),
                Identifier.fromNamespaceAndPath("nerocompanion", "carry"),
                Identifier.fromNamespaceAndPath("nerocompanion", "guard_target"));
        assertTrue(WorkGoals.activeFor(work, CompanionMode.SLEEP).isEmpty());
    }

    @Test
    void loginWakesAtTheNextEvaluation() {
        Throttle t = CompanionScheduler.evaluate(input(true, true, false), LIMITS);
        assertFalse(t.sleeping());
        assertEquals(Throttle.Reason.WORKING, t.reason());
    }

    @Test
    void salvageKeepsACompanionAwakeOnlyForAnOnlineOwnerAndWithBattery() {
        Throttle awake = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, false,
                Double.MAX_VALUE, false, false, false, false, true), LIMITS);
        assertEquals(Throttle.Reason.SALVAGING, awake.reason(), "owner respawned elsewhere: still gathering");
        Throttle flat = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, false, true,
                true, false, true), LIMITS);
        assertTrue(flat.sleeping(), "an empty battery still sleeps");
        Throttle near = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, false, false,
                false, true, true), LIMITS);
        assertEquals(Throttle.Reason.NEAR_OWNER, near.reason(),
                "with the owner here the normal rules apply; SALVAGING (salvage goal only) means the owner is away");
    }

    @Test
    void onlyTheOwnerMayUnparkABayRecord() {
        UUID owner = UUID.randomUUID();
        CompanionRecord parked = CompanionRecord.fresh(Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"),
                owner, CompanionMode.CARRY, 10L, 1L);
        assertTrue(BaySlots.mayUnpark(parked, owner));
        assertFalse(BaySlots.mayUnpark(parked, UUID.randomUUID()), "another player never unparks it");
        assertFalse(BaySlots.mayUnpark(parked, null));
        assertFalse(BaySlots.mayUnpark(parked.forNetwork(), owner), "an ownerless record is nobody's");
    }
}
