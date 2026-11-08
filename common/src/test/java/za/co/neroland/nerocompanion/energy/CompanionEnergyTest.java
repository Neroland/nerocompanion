package za.co.neroland.nerocompanion.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.scheduler.CompanionLimits;
import za.co.neroland.nerocompanion.scheduler.CompanionScheduler;
import za.co.neroland.nerocompanion.scheduler.Throttle;

/**
 * Headless energy drain: simulate the scheduler's once-per-20-ticks pass over a personal-drone
 * buffer and check the full-to-empty times, the low-battery rule and the forced sleep at 0 FE.
 */
class CompanionEnergyTest {

    private static final long CAPACITY = 24_000L;
    private static final long IDLE = 10L;
    private static final long ACTIVE = 40L;

    /** Runs evaluations every 20 ticks until empty; returns the seconds it took. */
    private static long secondsToEmpty(CompanionEnergy.Activity activity) {
        CompanionEnergy energy = new CompanionEnergy(CAPACITY, CAPACITY);
        long seconds = 0;
        for (long tick = 0; !energy.isEmpty(); tick++) {
            if (CompanionScheduler.isEvaluationTick(tick, 0)) {
                energy.drainSecond(activity, IDLE, ACTIVE);
                seconds++;
            }
        }
        return seconds;
    }

    @Test
    void idleDroneLastsFortyMinutes() {
        assertEquals(40L * 60L, secondsToEmpty(CompanionEnergy.Activity.IDLE));
    }

    @Test
    void activeDroneLastsTenMinutes() {
        assertEquals(10L * 60L, secondsToEmpty(CompanionEnergy.Activity.ACTIVE));
    }

    @Test
    void sleepDrainsNothing() {
        CompanionEnergy energy = new CompanionEnergy(CAPACITY, 500L);
        for (int i = 0; i < 10_000; i++) {
            energy.drainSecond(CompanionEnergy.Activity.SLEEP, IDLE, ACTIVE);
        }
        assertEquals(500L, energy.stored());
    }

    @Test
    void lowBatteryAlertsOncePerDischargeAndRearmsAbove50() {
        CompanionEnergy energy = new CompanionEnergy(CAPACITY, CAPACITY);
        int alerts = 0;
        while (!energy.isEmpty()) {
            energy.drainSecond(CompanionEnergy.Activity.ACTIVE, IDLE, ACTIVE);
            if (energy.takeLowAlert(CompanionEnergy.DEFAULT_LOW_PERCENT)) {
                alerts++;
                assertTrue(energy.percent() <= 10);
            }
        }
        assertEquals(1, alerts);
        energy.insert(CAPACITY / 4); // 25 %: not re-armed yet
        energy.drainSecond(CompanionEnergy.Activity.ACTIVE, IDLE, (CAPACITY / 4) - 100);
        assertFalse(energy.takeLowAlert(CompanionEnergy.DEFAULT_LOW_PERCENT));
        energy.insert(CAPACITY); // full: re-armed
        energy.drainSecond(CompanionEnergy.Activity.ACTIVE, IDLE, CAPACITY - 100);
        assertTrue(energy.takeLowAlert(CompanionEnergy.DEFAULT_LOW_PERCENT));
    }

    @Test
    void lowWithoutHomeAndEmptyForceSleep() {
        CompanionLimits limits = CompanionLimits.DEFAULTS;
        Throttle lowNoHome = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, false,
                false, true, false), limits);
        assertEquals(Throttle.Reason.LOW_ENERGY_NO_HOME, lowNoHome.reason());
        assertTrue(lowNoHome.sleeping());
        Throttle lowWithHome = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, false,
                false, true, true), limits);
        assertFalse(lowWithHome.sleeping());
        Throttle empty = CompanionScheduler.evaluate(new CompanionScheduler.Input(true, true, true, 4.0D, false,
                true, true, true), limits);
        assertEquals(Throttle.Reason.NO_ENERGY, empty.reason());
    }

    @Test
    void aCompanionLoadedLowDoesNotAlertAgain() {
        CompanionEnergy low = new CompanionEnergy(CAPACITY, CAPACITY / 20);
        low.restoreAlertState(CompanionEnergy.DEFAULT_LOW_PERCENT);
        assertFalse(low.takeLowAlert(CompanionEnergy.DEFAULT_LOW_PERCENT));
        CompanionEnergy full = new CompanionEnergy(CAPACITY, CAPACITY);
        full.restoreAlertState(CompanionEnergy.DEFAULT_LOW_PERCENT);
        full.drainSecond(CompanionEnergy.Activity.ACTIVE, IDLE, CAPACITY - 10);
        assertTrue(full.takeLowAlert(CompanionEnergy.DEFAULT_LOW_PERCENT));
    }

    @Test
    void capacityChangeClampsStoredEnergy() {
        CompanionEnergy energy = new CompanionEnergy(30_000L, 30_000L);
        energy.setCapacity(24_000L);
        assertEquals(24_000L, energy.stored());
        assertEquals(100, energy.percent());
    }

    @Test
    void perActionCostsAreAllOrNothing() {
        CompanionEnergy energy = new CompanionEnergy(1_000L, 100L);
        assertFalse(energy.tryPay(120L));
        assertEquals(100L, energy.stored());
        assertTrue(energy.tryPay(100L));
        assertTrue(energy.isEmpty());
        assertEquals(0, energy.percent());
    }
}
