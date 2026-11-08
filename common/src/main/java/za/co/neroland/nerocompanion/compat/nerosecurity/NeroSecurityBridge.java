package za.co.neroland.nerocompanion.compat.nerosecurity;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The <b>only</b> class that names a NeroSecurity type; loaded only from {@link NeroSecurityCompat}. Compiled against
 * the never-packaged stubs (tag {@code v0.5.0-beta.1}).
 */
final class NeroSecurityBridge {

    private NeroSecurityBridge() {
    }

    static void probe() {
        SecurityInstallations.class.getName();
        AccessResolver.class.getName();
    }

    /** True when no installation covers the device at {@code pos}, or the owner passes its {@code OPEN} check. */
    static boolean allowed(ServerPlayer owner, ServerLevel level, BlockPos pos) {
        DevicePos at = DevicePos.of(level, pos);
        Optional<Installation> installation = SecurityInstallations.get(level.getServer()).atDevice(at);
        if (installation.isEmpty()) {
            return true;
        }
        return AccessResolver.DEFAULT.resolve(owner, installation.get(), AccessAction.OPEN, at).allowed();
    }
}
