package za.co.neroland.nerocompanion.compat.nerospace;

/** The two off-world rules (decision 020), pure so they are unit-tested without a world. */
public final class OffWorldRules {

    /** Flight speed never drops below this share of normal, however heavy the world. */
    public static final double MIN_FLIGHT_FACTOR = 0.8D;
    /** Flight speed never rises above this share of normal, however light the world. */
    public static final double MAX_FLIGHT_FACTOR = 1.25D;

    private OffWorldRules() {
    }

    /**
     * A flying companion's speed factor at {@code gravity} (1.0 = Earth): {@code 1 / sqrt(g)}, clamped to
     * {@value #MIN_FLIGHT_FACTOR}..{@value #MAX_FLIGHT_FACTOR}; exactly 1.0 for Earth gravity or a nonsense value.
     */
    public static double flightFactor(double gravity) {
        if (!(gravity > 0.0D) || Double.isInfinite(gravity) || gravity == 1.0D) {
            return 1.0D;
        }
        double factor = 1.0D / Math.sqrt(gravity);
        return Math.max(MIN_FLIGHT_FACTOR, Math.min(MAX_FLIGHT_FACTOR, factor));
    }

    /** Whether radiation forces the companion to sleep: irradiated and no Rad Shielding chip. */
    public static boolean sleepsInHazard(OffWorld where, boolean radShielding) {
        return where.radiation() && !radShielding;
    }
}
