package za.co.neroland.nerocompanion.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadialLayoutTest {

    private static int pick(double dx, double dy) {
        return RadialLayout.pick(dx, dy, true, false, RadialLayout.NONE);
    }

    @Test
    void wedgesRunClockwiseFromTheTop() {
        assertEquals(0, pick(0, -25));
        assertEquals(1, pick(18, -18));
        assertEquals(2, pick(25, 0));
        assertEquals(3, pick(18, 18));
        assertEquals(4, pick(0, 25));
        assertEquals(5, pick(-18, 18));
        assertEquals(6, pick(-25, 0));
        assertEquals(7, pick(-18, -18));
    }

    @Test
    void sectorEdgesSplitHalfway() {
        double r = 25;
        double just = Math.toRadians(22.0);
        assertEquals(0, pick(Math.sin(just) * r, -Math.cos(just) * r));
        double past = Math.toRadians(23.0);
        assertEquals(1, pick(Math.sin(past) * r, -Math.cos(past) * r));
        assertEquals(0, pick(-Math.sin(just) * r, -Math.cos(just) * r));
        assertEquals(7, pick(-Math.sin(past) * r, -Math.cos(past) * r));
    }

    @Test
    void hubNeedsMovementOrADeliberateClick() {
        assertEquals(RadialLayout.NONE, RadialLayout.pick(0, 0, false, false, RadialLayout.NONE));
        assertEquals(RadialLayout.HUB, RadialLayout.pick(0, 0, false, true, RadialLayout.NONE));
        assertEquals(RadialLayout.HUB, RadialLayout.pick(2, 2, true, false, RadialLayout.NONE));
    }

    @Test
    void recallPillUnderTheRing() {
        assertEquals(RadialLayout.RECALL, pick(0, 64));
        assertEquals(RadialLayout.RECALL, pick(-48, 64));
        assertEquals(RadialLayout.RECALL, pick(47, 77));
    }

    @Test
    void centreKeepsThePillAboveTheHotbarAndThePlateOnScreen() {
        // 240 px tall (1280x720 at scale 3): raised from 120 so the pill ends 62 px above the bottom.
        assertEquals(100, RadialLayout.centreY(240));
        assertEquals(100 + RadialLayout.PILL_Y + RadialLayout.PILL_H, 240 - 62);
        // Tall screens keep the middle.
        assertEquals(270, RadialLayout.centreY(540));
        // Very short screens keep the name plate on screen.
        assertEquals(68, RadialLayout.centreY(160));
    }

    @Test
    void nothingIsSelectedBeforeTheCursorMoves() {
        // A raised ring (short screens) opens with the cursor below its centre, inside the bottom wedge.
        assertEquals(RadialLayout.NONE, RadialLayout.pick(0, 20, false, false, RadialLayout.NONE));
        assertEquals(RadialLayout.Action.STAY_OPEN,
                RadialLayout.onUseRelease(RadialLayout.pick(0, 20, false, false, RadialLayout.NONE)));
        assertEquals(4, RadialLayout.pick(0, 20, true, false, RadialLayout.NONE));
    }

    @Test
    void farOutsideSelectsNothingUnlessAWedgeWasSelected() {
        assertEquals(RadialLayout.NONE, pick(0, -100));
        assertEquals(0, RadialLayout.pick(0, -100, true, false, 0));
        // A flick past the ring keeps a wedge, and the angle still decides which one.
        assertEquals(2, RadialLayout.pick(100, 0, true, false, 0));
    }

    @Test
    void movementThreshold() {
        assertFalse(RadialLayout.movedOff(3, 3));
        assertTrue(RadialLayout.movedOff(4, 0));
        assertTrue(RadialLayout.movedOff(0, -4));
    }

    @Test
    void leftClickConfirmsAnyElementAndClosesOnEmptySpace() {
        for (int i = 0; i < 8; i++) {
            assertEquals(RadialLayout.Action.CONFIRM, RadialLayout.onClick(i));
        }
        assertEquals(RadialLayout.Action.CONFIRM, RadialLayout.onClick(RadialLayout.HUB));
        assertEquals(RadialLayout.Action.CONFIRM, RadialLayout.onClick(RadialLayout.RECALL));
        assertEquals(RadialLayout.Action.CLOSE, RadialLayout.onClick(RadialLayout.NONE));
    }

    @Test
    void lettingGoOfTheRemoteWithNothingSelectedKeepsTheRadialOpen() {
        // The owner's bug: let go of the Remote, then left-click a wedge. The release must not close the radial.
        int atOpen = RadialLayout.pick(0, 0, false, false, RadialLayout.NONE);
        assertEquals(RadialLayout.Action.STAY_OPEN, RadialLayout.onUseRelease(atOpen));
        int clicked = RadialLayout.pick(25, 0, true, true, atOpen);
        assertEquals(2, clicked);
        assertEquals(RadialLayout.Action.CONFIRM, RadialLayout.onClick(clicked));
    }

    @Test
    void holdAimAndReleaseStillConfirms() {
        assertEquals(RadialLayout.Action.CONFIRM, RadialLayout.onUseRelease(3));
        assertEquals(RadialLayout.Action.CONFIRM, RadialLayout.onUseRelease(RadialLayout.HUB));
    }
}
