package za.co.neroland.nerocompanion.compat.nerospace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerolandcore.worldgen.SpaceTags;

import za.co.neroland.nerocompanion.compat.SiblingCompat;

/**
 * Nerospace façade (decision 020). "Off-Earth" comes from Core's {@code SpaceTags} (empty on a Core-only server, so
 * every level is Earth there); only an off-Earth level asks Nerospace for its gravity and radiation. Companions keep
 * working off-world: the only effects are a flying companion's speed ({@link OffWorldRules#flightFactor}) and a
 * forced sleep in radiation without the Rad Shielding chip ({@link OffWorldRules#sleepsInHazard}).
 */
public final class NerospaceCompat {

    public static final String MOD_ID = "nerospace";

    private static volatile boolean active;

    private NerospaceCompat() {
    }

    public static void init() {
        init(SiblingCompat.isLoaded(MOD_ID));
    }

    static synchronized void init(boolean loaded) {
        if (!loaded || active) {
            return;
        }
        try {
            NerospaceBridge.probe();
            active = true;
            SiblingCompat.enabled(MOD_ID, "companions follow planet gravity and radiation");
        } catch (RuntimeException | LinkageError failure) {
            SiblingCompat.failed(MOD_ID, failure);
        }
    }

    public static boolean isActive() {
        return active;
    }

    static synchronized void reset() {
        active = false;
    }

    /** The off-world facts at {@code pos}; {@link OffWorld#EARTH} unless Core's space tags list this level. */
    public static OffWorld environment(ServerLevel level, BlockPos pos) {
        boolean space;
        try {
            space = SpaceTags.isSpace(level);
        } catch (RuntimeException failure) {
            space = false;
        }
        return environment(space, level, pos);
    }

    /** {@link #environment(ServerLevel, BlockPos)} with the off-Earth answer already known (tests pass it). */
    static OffWorld environment(boolean offEarth, ServerLevel level, BlockPos pos) {
        if (!offEarth) {
            return OffWorld.EARTH;
        }
        if (!active) {
            return OffWorld.SPACE_UNKNOWN;
        }
        try {
            return NerospaceBridge.environment(level, pos);
        } catch (RuntimeException | LinkageError failure) {
            active = false;
            SiblingCompat.failed(MOD_ID, failure);
            return OffWorld.SPACE_UNKNOWN;
        }
    }
}
