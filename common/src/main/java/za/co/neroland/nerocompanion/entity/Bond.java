package za.co.neroland.nerocompanion.entity;

/** Bond levels (0-4) from bond points; the thresholds live in {@code bond.BondLevels} (one source). Cosmetic only. */
public final class Bond {

    private Bond() {
    }

    /** Points still needed for the next level, or -1 at the top level. */
    public static int pointsToNext(int points) {
        return za.co.neroland.nerocompanion.bond.BondLevels.pointsToNext(points);
    }

    public static int level(int points) {
        return za.co.neroland.nerocompanion.bond.BondLevels.level(points);
    }
}
