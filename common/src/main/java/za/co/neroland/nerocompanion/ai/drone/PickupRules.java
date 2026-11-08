package za.co.neroland.nerocompanion.ai.drone;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * The Personal Drone's carry-lite rules (pure).
 *
 * <ul>
 *   <li><b>Cargo</b>: {@value #CARGO_SLOTS} slots (the type file's {@code cargo_slots}; a Cargo Rack adds 9).</li>
 *   <li><b>Pickup</b>: item entities within {@value #PICKUP_RADIUS} blocks of the drone that its <i>owner</i> could
 *       pick up right now: alive, no pickup delay, and not thrown by somebody else. Never another player's thrown
 *       item, never another player's death drop while their salvage window is open, never an item still in its
 *       pickup delay, never in a place the owner may not enter.</li>
 *   <li><b>When</b>: in CARRY mode it collects everything in reach; in FOLLOW mode it only collects what the owner has
 *       no room for (an overflow buffer), and hands items back as soon as the owner has room again and is within
 *       {@value #HAND_BACK_DISTANCE} blocks.</li>
 * </ul>
 */
public final class PickupRules {

    public static final int CARGO_SLOTS = 8;
    public static final double PICKUP_RADIUS = 4.0D;
    public static final double GRAB_DISTANCE = 1.5D;
    public static final double HAND_BACK_DISTANCE = 4.0D;
    public static final int SCAN_TICKS = 10;
    /** Give up on an item after this long without reaching it. */
    public static final int GIVE_UP_TICKS = 100;
    /** Give up early if the navigator has stopped (no path) after this long. */
    public static final int STALLED_TICKS = 40;

    private PickupRules() {
    }

    /**
     * Whether the drone may take this item entity.
     *
     * @param thrower the UUID of whoever threw/dropped it, if the item records one
     * @param anotherPlayersDeathDrop whether it is another player's death drop whose salvage window is still open
     */
    public static boolean mayPickUp(boolean alive, boolean pickupDelay, @Nullable UUID thrower, @Nullable UUID owner,
            double distanceSq, boolean anotherPlayersDeathDrop) {
        if (!alive || pickupDelay || owner == null || anotherPlayersDeathDrop) {
            return false;
        }
        if (thrower != null && !thrower.equals(owner)) {
            return false;
        }
        return distanceSq <= PICKUP_RADIUS * PICKUP_RADIUS;
    }

    /** Whether the drone collects at all in {@code mode}, given whether the owner has room for the item. */
    public static boolean collects(CompanionMode mode, boolean ownerHasRoom) {
        return switch (mode) {
            case CARRY -> true;
            case FOLLOW -> !ownerHasRoom;
            default -> false;
        };
    }

    /** Whether the drone hands its cargo back to the owner unasked. */
    public static boolean handsBack(CompanionMode mode, boolean ownerHasRoom, double ownerDistanceSq) {
        return mode == CompanionMode.FOLLOW && ownerHasRoom
                && ownerDistanceSq <= HAND_BACK_DISTANCE * HAND_BACK_DISTANCE;
    }

    /**
     * Whether an item is close enough to the <i>owner</i> to chase: within {@value #PICKUP_RADIUS} blocks plus the
     * chassis' follow distance, so the drone never wanders off after an item while its owner walks away.
     */
    public static boolean withinOwnerReach(double ownerToItemSq, double followDistance) {
        double reach = PICKUP_RADIUS + Math.max(0.0D, followDistance);
        return ownerToItemSq <= reach * reach;
    }

    /** Whether to abandon the current item: too long, or the navigator stalled without arriving. */
    public static boolean giveUp(int ticksRunning, boolean navigationDone, boolean arrived) {
        return !arrived && (ticksRunning >= GIVE_UP_TICKS || (navigationDone && ticksRunning >= STALLED_TICKS));
    }
}
