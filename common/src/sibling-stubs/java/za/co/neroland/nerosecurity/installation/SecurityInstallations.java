package za.co.neroland.nerosecurity.installation;

import java.util.Optional;

import net.minecraft.server.MinecraftServer;

/**
 * COMPILE-TIME STUB, never packaged. Signature copy of NeroSecurity at tag {@code v0.5.0-beta.1} (unchanged in its
 * 0.6.0 tree). Only NeroCompanion's {@code compat/nerosecurity/NeroSecurityBridge} names it, and only after NeroSecurity
 * is known to be loaded, so at runtime the real class is always the one resolved. Bodies never run.
 */
public final class SecurityInstallations {

    private SecurityInstallations() {
    }

    public static SecurityInstallations get(MinecraftServer server) {
        throw new UnsupportedOperationException("stub");
    }

    public synchronized Optional<Installation> atDevice(DevicePos pos) {
        throw new UnsupportedOperationException("stub");
    }
}
