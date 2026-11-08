package za.co.neroland.nerocompanion.bond;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.Bond;

/** Bond level thresholds. */
class BondLevelsTest {

    @Test
    void thresholds() {
        assertEquals(0, BondLevels.level(0));
        assertEquals(0, BondLevels.level(99));
        assertEquals(1, BondLevels.level(100));
        assertEquals(1, BondLevels.level(299));
        assertEquals(2, BondLevels.level(300));
        assertEquals(3, BondLevels.level(700));
        assertEquals(3, BondLevels.level(1499));
        assertEquals(4, BondLevels.level(1500));
        assertEquals(4, BondLevels.level(BondLevels.MAX_POINTS));
        for (int level = 0; level <= BondLevels.MAX_LEVEL; level++) {
            assertEquals(level, BondLevels.level(BondLevels.threshold(level)));
        }
    }

    @Test
    void pointsToNext() {
        assertEquals(100, BondLevels.pointsToNext(0));
        assertEquals(1, BondLevels.pointsToNext(699));
        assertEquals(-1, BondLevels.pointsToNext(1500));
    }

    @Test
    void addClamps() {
        assertEquals(5, BondLevels.add(0, 5));
        assertEquals(0, BondLevels.add(3, -10));
        assertEquals(BondLevels.MAX_POINTS, BondLevels.add(BondLevels.MAX_POINTS - 1, Integer.MAX_VALUE));
    }

    @Test
    void agreesWithTheEntitySynchedLevel() {
        // The entity syncs DATA_BOND_LEVEL from entity.Bond; both must give the same level.
        for (int points = 0; points <= 3000; points++) {
            assertEquals(Bond.level(points), BondLevels.level(points), "points " + points);
            assertEquals(Bond.pointsToNext(points), BondLevels.pointsToNext(points), "points " + points);
        }
    }

    @Test
    void presenceAloneReachesLevelFourInAboutEightHours() {
        int samplesPerPoint = BondRules.PRESENCE_SECONDS / BondRules.POINTS_PER_PRESENCE;
        long seconds = (long) BondLevels.threshold(4) * samplesPerPoint;
        assertEquals(30_000L, seconds);
    }
}
