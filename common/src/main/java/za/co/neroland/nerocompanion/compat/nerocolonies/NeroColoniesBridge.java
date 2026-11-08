package za.co.neroland.nerocompanion.compat.nerocolonies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocolonies.colony.ColonyApi;

/** The <b>only</b> class that names a NeroColonies type; loaded only from {@link NeroColoniesCompat}. */
final class NeroColoniesBridge {

    private NeroColoniesBridge() {
    }

    static void probe() {
        ColonyApi.class.getName();
    }

    static boolean canBuild(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        return ColonyApi.canBuild(owner, level, pos);
    }
}
