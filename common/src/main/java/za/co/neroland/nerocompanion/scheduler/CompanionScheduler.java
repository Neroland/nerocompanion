package za.co.neroland.nerocompanion.scheduler;

/**
 * The distance and owner throttle, as pure functions the entity calls. Nothing here runs per tick except the two cheap
 * modulo checks; the {@link #evaluate} decision runs once every {@value #EVALUATION_INTERVAL} ticks per
 * companion, staggered by entity id so 40 companions do not all evaluate on the same tick.
 *
 * <table>
 *   <caption>Rules</caption>
 *   <tr><td>no owner, owner offline, or owner in another dimension (not working)</td><td>SLEEP</td></tr>
 *   <tr><td>gathering the owner's death drops with the owner elsewhere (online, battery not empty)</td>
 *       <td>FULL, salvage goal only ({@code SALVAGING})</td></tr>
 *   <tr><td>battery empty, or low with no reachable home</td><td>SLEEP</td></tr>
 *   <tr><td>in radiation (off-world) without the Rad Shielding chip</td><td>SLEEP ({@code HAZARD})</td></tr>
 *   <tr><td>distance &gt; sleepDistance, working or not</td><td>SLEEP (a job pauses; it resumes on return)</td></tr>
 *   <tr><td>working (owner online, same dimension, within sleepDistance)</td><td>FULL</td></tr>
 *   <tr><td>throttleDistance &lt; distance &le; sleepDistance</td><td>QUARTER</td></tr>
 *   <tr><td>otherwise</td><td>FULL</td></tr>
 * </table>
 */
public final class CompanionScheduler {

    /** Ticks between throttle evaluations (and energy drains). */
    public static final int EVALUATION_INTERVAL = 20;

    /** Ticks between goal scans (targets, items, ores); results are cached in between. */
    public static final int SCAN_INTERVAL = 10;

    /** In the QUARTER state the AI steps on one tick in this many. */
    public static final int QUARTER_STRIDE = 4;

    private CompanionScheduler() {
    }

    /** Facts the decision needs, gathered by the entity once per evaluation. */
    public record Input(boolean hasOwner, boolean ownerOnline, boolean sameDimension, double distanceSq,
            boolean working, boolean energyEmpty, boolean energyLow, boolean homeReachable, boolean salvaging,
            boolean hazard) {

        /** Without an off-world hazard (the Stage 4 shape). */
        public Input(boolean hasOwner, boolean ownerOnline, boolean sameDimension, double distanceSq, boolean working,
                boolean energyEmpty, boolean energyLow, boolean homeReachable, boolean salvaging) {
            this(hasOwner, ownerOnline, sameDimension, distanceSq, working, energyEmpty, energyLow, homeReachable,
                    salvaging, false);
        }

        /** Without death salvage (the Stage 1 shape). */
        public Input(boolean hasOwner, boolean ownerOnline, boolean sameDimension, double distanceSq, boolean working,
                boolean energyEmpty, boolean energyLow, boolean homeReachable) {
            this(hasOwner, ownerOnline, sameDimension, distanceSq, working, energyEmpty, energyLow, homeReachable,
                    false);
        }
    }

    public static Throttle evaluate(Input in, CompanionLimits limits) {
        if (!in.hasOwner()) {
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.NO_OWNER);
        }
        if (!in.ownerOnline()) {
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.OWNER_OFFLINE);
        }
        if (in.energyEmpty()) {
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.NO_ENERGY);
        }
        if (in.energyLow() && !in.homeReachable()) {
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.LOW_ENERGY_NO_HOME);
        }
        if (in.hazard()) {
            // Radiation without shielding: powered down where it stands, salvage and work included.
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.HAZARD);
        }
        if (!in.sameDimension()) {
            if (in.salvaging()) {
                // Gathering the owner's death drops (60 s at most) while the owner respawned elsewhere: awake, but
                // only the salvage goal runs - never mining, depositing or attacking with the owner away.
                return new Throttle(Throttle.State.FULL, Throttle.Reason.SALVAGING);
            }
            // Work never continues across dimensions: MINE/deposit/attack need the owner here.
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.OWNER_OTHER_DIMENSION);
        }
        double sleep = limits.sleepDistance();
        double throttle = limits.throttleDistance();
        if (in.distanceSq() > sleep * sleep) {
            if (in.salvaging()) {
                return new Throttle(Throttle.State.FULL, Throttle.Reason.SALVAGING);
            }
            // Work never runs with the owner out of range: a bot is not a way to dig or haul where its owner is not.
            return new Throttle(Throttle.State.SLEEP, Throttle.Reason.TOO_FAR);
        }
        if (in.working()) {
            return new Throttle(Throttle.State.FULL, Throttle.Reason.WORKING);
        }
        if (in.distanceSq() > throttle * throttle) {
            return new Throttle(Throttle.State.QUARTER, Throttle.Reason.THROTTLED_DISTANCE);
        }
        return new Throttle(Throttle.State.FULL, Throttle.Reason.NEAR_OWNER);
    }

    /** Whether this tick is the companion's evaluation tick (once per {@value #EVALUATION_INTERVAL}). */
    public static boolean isEvaluationTick(long tickCount, int entityId) {
        return Math.floorMod(tickCount + entityId, EVALUATION_INTERVAL) == 0;
    }

    /** Whether this tick is a goal-scan tick (once per {@value #SCAN_INTERVAL}). */
    public static boolean isScanTick(long tickCount, int entityId) {
        return Math.floorMod(tickCount + entityId, SCAN_INTERVAL) == 0;
    }

    /** Whether the AI ({@code serverAiStep}) runs on this tick. */
    public static boolean stepsAi(Throttle.State state, long tickCount, int entityId) {
        return switch (state) {
            case FULL -> true;
            case QUARTER -> Math.floorMod(tickCount + entityId, QUARTER_STRIDE) == 0;
            case SLEEP -> false;
        };
    }
}
