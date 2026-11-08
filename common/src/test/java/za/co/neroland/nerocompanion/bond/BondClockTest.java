package za.co.neroland.nerocompanion.bond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bond accrual: presence (time-based), pets, jobs. */
class BondClockTest {

    @Test
    void presenceEarnsOnePointPerTwentyWindows() {
        BondClock clock = new BondClock();
        int points = 0;
        long now = 1000L;
        for (int i = 0; i < BondRules.PRESENCE_SECONDS * 3; i++) {
            points += clock.sample(true, now);
            now += BondClock.SAMPLE_TICKS;
        }
        assertEquals(3 * BondRules.POINTS_PER_PRESENCE, points);
    }

    @Test
    void forcedEvaluationsDoNotSpeedUpPresence() {
        // A macro toggling modes forces many evaluations per window: only one sample per 20 ticks counts.
        BondClock clock = new BondClock();
        for (int i = 0; i < 100; i++) {
            clock.sample(true, 5000L);
        }
        assertEquals(1, clock.secondsNear());
        int points = 0;
        // Ten evaluations a second for 200 s of game time.
        for (long tick = 5001L; tick < 5000L + 4000L; tick += 2L) {
            points += clock.sample(true, tick);
        }
        // 4000 ticks = 200 windows (+1 from before) -> 10 points, not 100.
        assertEquals(200 / BondRules.PRESENCE_SECONDS * BondRules.POINTS_PER_PRESENCE, points);
    }

    @Test
    void awaySamplesEarnNothingAndKeepProgress() {
        BondClock clock = new BondClock();
        long now = 0L;
        for (int i = 0; i < BondRules.PRESENCE_SECONDS - 1; i++) {
            assertEquals(0, clock.sample(true, now));
            now += BondClock.SAMPLE_TICKS;
        }
        assertEquals(0, clock.sample(false, now));
        now += BondClock.SAMPLE_TICKS;
        assertEquals(BondRules.PRESENCE_SECONDS - 1, clock.secondsNear());
        assertEquals(BondRules.POINTS_PER_PRESENCE, clock.sample(true, now));
    }

    @Test
    void petCooldown() {
        BondClock clock = new BondClock();
        assertTrue(clock.petReady(100L));
        assertEquals(BondRules.POINTS_PER_PET, clock.pet(100L));
        assertFalse(clock.petReady(101L));
        assertEquals(0, clock.pet(101L));
        assertEquals(0, clock.pet(100L + BondRules.PET_COOLDOWN_TICKS - 1));
        assertEquals(BondRules.POINTS_PER_PET, clock.pet(100L + BondRules.PET_COOLDOWN_TICKS));
    }

    @Test
    void spamPettingCannotOutpaceTheCooldown() {
        BondClock clock = new BondClock();
        int points = 0;
        for (long tick = 0; tick < 72_000L; tick++) {
            points += clock.pet(tick);
        }
        assertEquals(72_000 / BondRules.PET_COOLDOWN_TICKS * BondRules.POINTS_PER_PET, points);
    }

    @Test
    void jobCooldown() {
        BondClock clock = new BondClock();
        assertEquals(BondRules.POINTS_PER_JOB, clock.job(0L));
        assertEquals(0, clock.job(BondRules.JOB_COOLDOWN_TICKS - 1));
        assertEquals(BondRules.POINTS_PER_JOB, clock.job(BondRules.JOB_COOLDOWN_TICKS));
    }

    @Test
    void stampFromAnotherWorldsClockCountsAsElapsed() {
        BondClock clock = new BondClock();
        assertEquals(BondRules.POINTS_PER_PET, clock.pet(1_000_000L));
        assertEquals(BondRules.POINTS_PER_PET, clock.pet(50L));
    }
}
