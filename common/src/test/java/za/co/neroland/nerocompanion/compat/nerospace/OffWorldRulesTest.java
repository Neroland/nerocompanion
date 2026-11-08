package za.co.neroland.nerocompanion.compat.nerospace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.scheduler.CompanionLimits;
import za.co.neroland.nerocompanion.scheduler.CompanionScheduler;
import za.co.neroland.nerocompanion.scheduler.Throttle;

/** Off-world rules: flight follows gravity within bounds, radiation sleeps a companion unless shielded. */
class OffWorldRulesTest {

    @Test
    void flightFactorFollowsGravityWithinBounds() {
        assertEquals(1.0D, OffWorldRules.flightFactor(1.0D));
        assertEquals(1.0D, OffWorldRules.flightFactor(0.0D), "nonsense gravity: Earth");
        assertEquals(1.0D, OffWorldRules.flightFactor(Double.NaN));
        assertEquals(1.0D, OffWorldRules.flightFactor(Double.POSITIVE_INFINITY));
        assertEquals(OffWorldRules.MAX_FLIGHT_FACTOR, OffWorldRules.flightFactor(0.16D), 1e-9, "the Moon: capped");
        assertEquals(1.0D / Math.sqrt(0.8D), OffWorldRules.flightFactor(0.8D), 1e-9);
        assertEquals(OffWorldRules.MIN_FLIGHT_FACTOR, OffWorldRules.flightFactor(3.0D), 1e-9, "heavy: floored");
        assertTrue(OffWorldRules.flightFactor(0.5D) > 1.0D);
        assertTrue(OffWorldRules.flightFactor(1.3D) < 1.0D);
    }

    @Test
    void radiationSleepsUnlessShielded() {
        OffWorld irradiated = new OffWorld(true, 0.4D, true);
        assertTrue(OffWorldRules.sleepsInHazard(irradiated, false));
        assertFalse(OffWorldRules.sleepsInHazard(irradiated, true), "Rad Shielding chip");
        assertFalse(OffWorldRules.sleepsInHazard(OffWorld.EARTH, false));
        assertFalse(OffWorldRules.sleepsInHazard(OffWorld.SPACE_UNKNOWN, false), "no planet data: keeps working");
    }

    @Test
    void schedulerSleepsOnHazardEvenWhileWorking() {
        Throttle t = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, true, false,
                false, true, false, true), CompanionLimits.DEFAULTS);
        assertEquals(Throttle.State.SLEEP, t.state());
        assertEquals(Throttle.Reason.HAZARD, t.reason());
        Throttle fine = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, true, false,
                false, true, false, false), CompanionLimits.DEFAULTS);
        assertEquals(Throttle.Reason.WORKING, fine.reason(), "off-world without radiation: keeps working");
    }

    @Test
    void inactiveFacadeNeverAsksNerospace() {
        assertEquals(OffWorld.EARTH, NerospaceCompat.environment(false, null, null));
        assertEquals(OffWorld.SPACE_UNKNOWN, NerospaceCompat.environment(true, null, null));
    }
}
