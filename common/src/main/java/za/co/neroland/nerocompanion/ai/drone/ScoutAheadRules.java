package za.co.neroland.nerocompanion.ai.drone;

/** The Scout's lead-position numbers and pure helpers. */
public final class ScoutAheadRules {

    public static final double AHEAD = 12.0D;
    public static final double MIN_AHEAD = 2.0D;
    public static final double LEASH = 24.0D;
    /** Heights above the owner's feet tried in order: over the head in the open, at head height in a 2-high tunnel. */
    public static final double[] HEIGHTS = {2.0D, 1.5D, 1.0D};
    /** A new target closer than this to the current one is ignored. */
    public static final double DEAD_BAND = 3.0D;

    private ScoutAheadRules() {
    }

    /** The unit horizontal look direction {@code [x, z]}; from the yaw when looking straight up or down. */
    public static double[] horizontal(double lookX, double lookZ, float yawDegrees) {
        double h = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (h < 1.0E-4D) {
            double yaw = Math.toRadians(yawDegrees);
            return new double[] {-Math.sin(yaw), Math.cos(yaw)};
        }
        return new double[] {lookX / h, lookZ / h};
    }

    /** Whether the drone should switch from target {@code a} to {@code b} (outside the dead band). */
    public static boolean retarget(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz >= DEAD_BAND * DEAD_BAND;
    }
}
