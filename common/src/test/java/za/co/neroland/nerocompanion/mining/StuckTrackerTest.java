package za.co.neroland.nerocompanion.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Stuck detection is progress-based; repeated unreachable targets make the job give up; back-offs expire. */
class StuckTrackerTest {

    @Test
    void noPathTwiceIsUnreachableWithinTwoRepaths() {
        StuckTracker tracker = new StuckTracker();
        assertFalse(tracker.check(false, 10.0D, 0.0D));
        assertTrue(tracker.check(false, 10.0D, 0.0D));
    }

    @Test
    void aLongWalkThatKeepsGainingIsNeverStuck() {
        StuckTracker tracker = new StuckTracker();
        double distance = 60.0D;
        for (int i = 0; i < 100; i++) {
            distance -= 1.0D;
            assertFalse(tracker.check(true, distance, 1.0D));
        }
    }

    @Test
    void standingStillWithAPathIsStuckAfterTheProgressLimit() {
        StuckTracker tracker = new StuckTracker();
        for (int i = 0; i < StuckTracker.NO_PROGRESS_LIMIT; i++) {
            assertFalse(tracker.check(true, 6.0D, 0.0D), "check " + i);
        }
        assertTrue(tracker.check(true, 6.0D, 0.0D));
        tracker.reset();
        assertFalse(tracker.check(true, 6.0D, 0.0D));
    }

    @Test
    void theJobGivesUpAfterRepeatedUnreachableTargetsAndAReachStartsOver() {
        StuckTracker tracker = new StuckTracker();
        for (int i = 1; i < StuckTracker.GIVE_UP_AFTER; i++) {
            assertFalse(tracker.unreachable());
        }
        tracker.reached();
        assertEquals(0, tracker.unreachableInARow());
        for (int i = 1; i < StuckTracker.GIVE_UP_AFTER; i++) {
            assertFalse(tracker.unreachable());
        }
        assertTrue(tracker.unreachable());
    }

    @Test
    void aBackedOffStageIsSkippedThenRetried() {
        Backoff backoff = new Backoff();
        backoff.fail(DropRouter.Stage.BOUND_CONTAINER, 100L);
        assertTrue(backoff.blocked(DropRouter.Stage.BOUND_CONTAINER, 101L));
        assertFalse(backoff.blocked(DropRouter.Stage.OWNER, 101L));
        assertFalse(backoff.blocked(DropRouter.Stage.BOUND_CONTAINER, 100L + Backoff.TICKS));
        backoff.fail(DropRouter.Stage.OWNER, 0L);
        backoff.clear(DropRouter.Stage.OWNER);
        assertFalse(backoff.blocked(DropRouter.Stage.OWNER, 1L));
    }
}
