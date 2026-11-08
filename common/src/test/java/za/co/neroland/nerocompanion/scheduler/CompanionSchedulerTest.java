package za.co.neroland.nerocompanion.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The throttle schedule. */
class CompanionSchedulerTest {

    private static final CompanionLimits LIMITS = CompanionLimits.DEFAULTS;

    private static Throttle at(double distance) {
        return CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, distance * distance, false,
                false, false, false), LIMITS);
    }

    @Test
    void fullRateWithin32() {
        assertEquals(Throttle.State.FULL, at(5).state());
        assertEquals(Throttle.State.FULL, at(32).state());
    }

    @Test
    void quarterRateBetween32And64() {
        assertEquals(Throttle.State.QUARTER, at(32.5).state());
        assertEquals(Throttle.State.QUARTER, at(64).state());
    }

    @Test
    void sleepBeyond64() {
        Throttle far = at(64.5);
        assertTrue(far.sleeping());
        assertEquals(Throttle.Reason.TOO_FAR, far.reason());
    }

    @Test
    void ownerOfflineOrElsewhereSleeps() {
        assertEquals(Throttle.Reason.OWNER_OFFLINE, CompanionScheduler.evaluate(new CompanionScheduler.Input(true,
                false, false, Double.MAX_VALUE, true, false, false, false), LIMITS).reason());
        assertEquals(Throttle.Reason.OWNER_OTHER_DIMENSION, CompanionScheduler.evaluate(
                new CompanionScheduler.Input(true, true, false, Double.MAX_VALUE, true, false, false, false),
                LIMITS).reason());
        assertEquals(Throttle.Reason.NO_OWNER, CompanionScheduler.evaluate(new CompanionScheduler.Input(false,
                false, false, 0, false, false, false, false), LIMITS).reason());
    }

    @Test
    void workingRunsFullRateWithinSleepDistanceAndPausesBeyondIt() {
        Throttle working = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 50.0 * 50.0,
                true, false, false, false), LIMITS);
        assertEquals(Throttle.State.FULL, working.state());
        assertEquals(Throttle.Reason.WORKING, working.reason());
        Throttle far = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 500.0 * 500.0,
                true, false, false, false), LIMITS);
        assertTrue(far.sleeping());
        assertEquals(Throttle.Reason.TOO_FAR, far.reason());
    }

    @Test
    void configuredDistancesAreRespected() {
        CompanionLimits tight = new CompanionLimits(4, 16, 8, 16, 10, 96);
        assertEquals(Throttle.State.QUARTER, CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true,
                true, 10 * 10, false, false, false, false), tight).state());
        assertTrue(CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 17 * 17, false, false,
                false, false), tight).sleeping());
    }

    @Test
    void evaluationEvery20TicksStaggeredById() {
        int hits = 0;
        for (long tick = 0; tick < 200; tick++) {
            if (CompanionScheduler.isEvaluationTick(tick, 7)) {
                hits++;
                assertEquals(0, (tick + 7) % 20);
            }
        }
        assertEquals(10, hits);
        assertFalse(CompanionScheduler.isEvaluationTick(0, 7) && CompanionScheduler.isEvaluationTick(0, 8));
    }

    @Test
    void scansEvery10Ticks() {
        int hits = 0;
        for (long tick = 0; tick < 100; tick++) {
            if (CompanionScheduler.isScanTick(tick, 3)) {
                hits++;
            }
        }
        assertEquals(10, hits);
    }

    @Test
    void aiStepsFollowTheState() {
        int quarter = 0;
        for (long tick = 0; tick < 400; tick++) {
            assertTrue(CompanionScheduler.stepsAi(Throttle.State.FULL, tick, 5));
            assertFalse(CompanionScheduler.stepsAi(Throttle.State.SLEEP, tick, 5));
            if (CompanionScheduler.stepsAi(Throttle.State.QUARTER, tick, 5)) {
                quarter++;
            }
        }
        assertEquals(100, quarter);
    }
}
