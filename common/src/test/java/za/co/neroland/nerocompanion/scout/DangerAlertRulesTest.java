package za.co.neroland.nerocompanion.scout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Danger = hostile within 8 blocks and behind the owner's look; one alert per cooldown and per mob. */
class DangerAlertRulesTest {

    @Test
    void behindWithinRangeIsDanger() {
        // Owner looks +Z.
        assertTrue(DangerAlertRules.isDanger(0, 1, 0, 0, -5));
        assertTrue(DangerAlertRules.isDanger(0, 1, 3, 1, -3));
        assertTrue(DangerAlertRules.isDanger(0, 1, 0, -6, 0));
    }

    @Test
    void inFrontOrTooFarIsNot() {
        assertFalse(DangerAlertRules.isDanger(0, 1, 0, 0, 5));
        assertFalse(DangerAlertRules.isDanger(0, 1, 0, 0, -9));
        assertFalse(DangerAlertRules.isDanger(1, 0, 6, 0, 0));
        assertFalse(DangerAlertRules.isDanger(0, 1, 0, 6, -6));
    }

    @Test
    void cooldownLimitsAlerts() {
        DangerAlertRules.Cooldown cooldown = new DangerAlertRules.Cooldown();
        assertTrue(cooldown.tryFire(100));
        assertFalse(cooldown.tryFire(110));
        assertFalse(cooldown.tryFire(159));
        assertTrue(cooldown.tryFire(160));
    }

    @Test
    void oncePerMobUntilItLeavesTheRange() {
        DangerAlertRules.Tracker tracker = new DangerAlertRules.Tracker();
        DangerAlertRules.Cooldown cooldown = new DangerAlertRules.Cooldown();
        assertTrue(tracker.check(Set.of(1), Set.of(1), cooldown, 0));
        assertFalse(tracker.check(Set.of(1), Set.of(1), cooldown, 100), "same mob circling: silent");
        assertFalse(tracker.check(Set.of(1), Set.of(), cooldown, 200));
        assertTrue(tracker.check(Set.of(1, 2), Set.of(2), cooldown, 300), "a new mob behind");
        assertFalse(tracker.check(Set.of(), Set.of(), cooldown, 400));
        assertTrue(tracker.check(Set.of(1), Set.of(1), cooldown, 500), "mob 1 left and came back");
    }

    @Test
    void combatSuppressesAlerts() {
        assertTrue(DangerAlertRules.inCombat(1000, 1030));
        assertFalse(DangerAlertRules.inCombat(1000, 1060));
        assertFalse(DangerAlertRules.inCombat(0, 30));
    }
}
