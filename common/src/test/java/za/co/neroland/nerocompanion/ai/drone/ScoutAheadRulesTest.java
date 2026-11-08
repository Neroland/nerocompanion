package za.co.neroland.nerocompanion.ai.drone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Scout's lead point: heights tried top-down, a dead band against darting, a direction even looking down. */
class ScoutAheadRulesTest {

    @Test
    void heightsFitATwoHighTunnel() {
        assertEquals(2.0D, ScoutAheadRules.HEIGHTS[0]);
        assertTrue(ScoutAheadRules.HEIGHTS[ScoutAheadRules.HEIGHTS.length - 1] < 2.0D);
    }

    @Test
    void deadBand() {
        assertFalse(ScoutAheadRules.retarget(0, 0, 0, 2, 0, 2));
        assertTrue(ScoutAheadRules.retarget(0, 0, 0, 3, 0, 0));
    }

    @Test
    void horizontalDirection() {
        assertArrayEquals(new double[] {0.6D, 0.8D}, ScoutAheadRules.horizontal(0.3D, 0.4D, 0F), 1.0E-9D);
        double[] down = ScoutAheadRules.horizontal(0.0D, 0.0D, 0F);
        assertArrayEquals(new double[] {0.0D, 1.0D}, down, 1.0E-9D);
    }
}
