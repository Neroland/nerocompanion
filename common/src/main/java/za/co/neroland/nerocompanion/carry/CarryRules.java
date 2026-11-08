package za.co.neroland.nerocompanion.carry;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Carry bot numbers and triggers, pure.
 *
 * <ul>
 *   <li><b>Pickup radius</b>: {@link #DEFAULT_PICKUP_RADIUS} blocks around the bot plus the Range chip's bonus,
 *       never more than {@link #MAX_PICKUP_RADIUS}.</li>
 *   <li><b>Deposit</b>: with the Auto-Deposit chip the bot runs to its bound container (or, unbound, to its owner)
 *       once its cargo is {@link #AUTO_DEPOSIT_FILL} full; without it, only when the owner asks (sneak-use the
 *       Remote on the bound container). A full bot stops picking up.</li>
 * </ul>
 */
public final class CarryRules {

    public static final int DEFAULT_PICKUP_RADIUS = 8;
    public static final int MAX_PICKUP_RADIUS = 16;

    /** Cargo fill (occupied slots / slots) at which the Auto-Deposit chip starts a run. */
    public static final double AUTO_DEPOSIT_FILL = 0.80D;

    /** How close (blocks, to the block centre) a bot must be to use a container or hand items over. */
    public static final double REACH = 2.5D;

    /** A bound container farther than this is not walked to. */
    public static final double CONTAINER_RANGE = 64.0D;

    private CarryRules() {
    }

    public static int pickupRadius(int typeRadius, int rangeBonus) {
        int base = typeRadius > 0 ? typeRadius : DEFAULT_PICKUP_RADIUS;
        return Math.max(1, Math.min(MAX_PICKUP_RADIUS, base + Math.max(0, rangeBonus)));
    }

    public static double fill(int occupiedSlots, int slots) {
        return slots <= 0 ? 1.0D : Math.max(0.0D, Math.min(1.0D, (double) occupiedSlots / slots));
    }

    /** Whether a deposit run should start now. Never with empty cargo. */
    public static boolean shouldDeposit(int occupiedSlots, int slots, boolean autoDepositChip, boolean requested) {
        if (occupiedSlots <= 0) {
            return false;
        }
        return requested || (autoDepositChip && fill(occupiedSlots, slots) >= AUTO_DEPOSIT_FILL);
    }

    /**
     * Whether a carry bot may take a lying item entity: alive, not empty, off its pickup delay, not thrown by
     * another player, and not another player's death drop while that player's salvage window is open.
     *
     * @param thrower the UUID of the player who threw it, or null when no player did (death drops have none)
     */
    public static boolean mayTake(boolean alive, boolean pickupDelay, boolean empty, @Nullable UUID thrower,
            @Nullable UUID owner, boolean anotherPlayersDeathDrop) {
        if (!alive || pickupDelay || empty || anotherPlayersDeathDrop) {
            return false;
        }
        return thrower == null || (owner != null && owner.equals(thrower));
    }

    /** Whether the bot may look for more items: some slot is free. */
    public static boolean mayPickUp(int occupiedSlots, int slots) {
        return slots > 0 && occupiedSlots < slots;
    }
}
