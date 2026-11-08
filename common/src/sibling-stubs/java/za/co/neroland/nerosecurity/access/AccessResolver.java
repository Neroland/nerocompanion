package za.co.neroland.nerosecurity.access;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * COMPILE-TIME STUB, never packaged. Signature copy of NeroSecurity at tag {@code v0.5.0-beta.1} (unchanged in its
 * 0.6.0 tree). Only NeroCompanion's {@code compat/nerosecurity/NeroSecurityBridge} names it, and only after NeroSecurity
 * is known to be loaded, so at runtime the real class is always the one resolved. Bodies never run.
 */
public final class AccessResolver {

    public static final AccessResolver DEFAULT = new AccessResolver();

    private AccessResolver() {
    }

    public AccessDecision resolve(ServerPlayer actor, @Nullable Installation installation, AccessAction action,
            @Nullable DevicePos at) {
        throw new UnsupportedOperationException("stub");
    }
}
