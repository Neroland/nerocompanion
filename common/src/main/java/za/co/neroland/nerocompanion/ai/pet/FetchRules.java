package za.co.neroland.nerocompanion.ai.pet;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * The fetch rules as pure functions (unit-tested without a game). A pet fetches only a Fetch Ball its own owner
 * threw: the thrower is the UUID {@code fetch.FetchTargets} recorded when the ball landed, compared with the pet's
 * stored owner UUID, never anyone's proximity.
 */
public final class FetchRules {

    /** Ball scans run on this cadence (ticks), never every tick. */
    public static final int SCAN_INTERVAL = 10;
    /** How far (blocks, horizontally) a pet looks for its owner's ball. */
    public static final double SCAN_RADIUS = 16.0D;
    /** Vertical reach of the scan. */
    public static final double SCAN_HEIGHT = 6.0D;
    /** Close enough to pick the ball up. */
    public static final double PICKUP_DISTANCE = 1.5D;
    /** Close enough to hand it back. */
    public static final double DELIVER_DISTANCE = 2.0D;
    /** A pet that has not reached the ball by then gives up (ticks). */
    public static final int SEEK_TIMEOUT = 600;
    /** Carrying, the pet walks back only within this distance; farther, following (and its teleport) takes over. */
    public static final double RETURN_LIMIT = 24.0D;

    private FetchRules() {
    }

    /** Whether a pet owned by {@code owner} may fetch a ball thrown by {@code thrower}. */
    public static boolean mayFetch(@Nullable UUID owner, @Nullable UUID thrower) {
        return owner != null && owner.equals(thrower);
    }

    /** Whether a seek that started at {@code started} has run out at {@code now}. */
    public static boolean seekTimedOut(long started, long now) {
        return now - started >= SEEK_TIMEOUT;
    }

    public static boolean withinPickup(double distanceSq) {
        return distanceSq <= PICKUP_DISTANCE * PICKUP_DISTANCE;
    }

    public static boolean withinDelivery(double distanceSq) {
        return distanceSq <= DELIVER_DISTANCE * DELIVER_DISTANCE;
    }

    public static boolean withinReturn(double distanceSq) {
        return distanceSq <= RETURN_LIMIT * RETURN_LIMIT;
    }
}
