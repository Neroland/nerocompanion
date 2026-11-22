package za.co.neroland.nerocompanion.compat.nerosecurity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.api.SecurityAccess;

/**
 * The <b>only</b> class that names a NeroSecurity type; loaded only from {@link NeroSecurityCompat}. Compiled
 * {@code compileOnly} against NeroSecurity's stable public API ({@code api.SecurityAccess}, 0.7.2 or later), never its
 * internal packages.
 */
final class NeroSecurityBridge {

    private NeroSecurityBridge() {
    }

    static void probe() {
        SecurityAccess.class.getName();
    }

    /** True when no installation covers the device at {@code pos}, or the owner passes its {@code OPEN} check. */
    static boolean allowed(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        return SecurityAccess.mayOpen(owner, level, pos);
    }
}
