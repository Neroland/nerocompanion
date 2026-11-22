package za.co.neroland.nerocompanion.compat.nerologistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.compat.SiblingCompat;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * NeroLogistics façade (decision 020). When a bot's bound block is an active network drive bay (or reaches an item
 * network), {@link #driveBayInlet}
 * returns a one-slot insert-only {@link Container} in front of it, so the carry and mining bots' existing deposit
 * path (bind, room check, {@code mayOpen}, deposit) works unchanged. With NeroLogistics absent nothing here loads a
 * NeroLogistics class and every call answers its no-op value.
 *
 * <p>Authority: NeroLogistics records no owners, so the deposit is gated by NeroCompanion's own owner-authority
 * chain only. Privacy: nothing about the player crosses this façade.
 */
public final class NeroLogisticsCompat {

    public static final String MOD_ID = "nerologistics";

    private static volatile boolean active;

    private NeroLogisticsCompat() {
    }

    /** Common init (compat detection step). */
    public static void init() {
        init(SiblingCompat.isLoaded(MOD_ID));
    }

    static synchronized void init(boolean loaded) {
        if (!loaded || active) {
            return;
        }
        try {
            NeroLogisticsBridge.probe();
            active = true;
            SiblingCompat.enabled(MOD_ID, "carry and mining bots can deposit into drive bays");
        } catch (RuntimeException | LinkageError failure) {
            SiblingCompat.failed(MOD_ID, failure);
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** Tests: back to the Core-only state. */
    static synchronized void reset() {
        active = false;
    }

    /**
     * An insert-only container in front of the drive bay at {@code pos}, or the item network reached from it (a
     * terminal or conduit), or null when there is neither or NeroLogistics is absent.
     */
    @Nullable
    public static Container driveBayInlet(ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity) {
        if (!active) {
            return null;
        }
        try {
            return NeroLogisticsBridge.inlet(level, pos, blockEntity);
        } catch (RuntimeException | LinkageError failure) {
            active = false;
            SiblingCompat.failed(MOD_ID, failure);
            return null;
        }
    }

    /**
     * The 0.7.0 seam: registering a bot as a network endpoint (so the network could route to it). NeroLogistics
     * declined a mobile-endpoint API (stale ids, crash leftovers, a server table of bot ids) in favour of stateless
     * {@code insertIntoNetwork}, which {@link #driveBayInlet} uses; this stays a no-op that reports false.
     */
    public static boolean registerEndpoint(CompanionEntity bot) {
        return false;
    }
}
