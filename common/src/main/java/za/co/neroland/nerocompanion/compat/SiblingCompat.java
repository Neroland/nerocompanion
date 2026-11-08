package za.co.neroland.nerocompanion.compat;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityProvider;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * What every sibling façade shares (decision 020): the guarded "is it installed" check, the one WARN when an
 * integration is switched off, and the owner lookup authority providers use. Never names a sibling type.
 *
 * <p>Privacy (POPIA/GDPR): the WARN names the sibling mod and the exception class only, never a player.
 */
public final class SiblingCompat {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private SiblingCompat() {
    }

    /** Whether {@code modId} is installed. False when the platform seam itself cannot answer (unit tests). */
    public static boolean isLoaded(String modId) {
        try {
            return Services.PLATFORM.isModLoaded(modId);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** One WARN per sibling when its bridge fails; the façade then answers its no-op value for the session. */
    public static void failed(String modId, Throwable failure) {
        if (WARNED.add(modId)) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] {} integration switched off ({})", modId,
                    failure.getClass().getSimpleName());
        }
    }

    /**
     * A sibling that guards authority (claims, secured devices, factions) is installed but its bridge failed: fail
     * <b>closed</b>. Registers {@code provider} (deny-only, see {@link #denying}) for the session and logs one WARN
     * naming the sibling and the exception class only, never a player.
     */
    public static void failedClosed(String modId, Throwable failure, AuthorityProvider provider) {
        OwnerAuthority.registerProvider(provider);
        if (WARNED.add(modId)) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] {} is installed but its integration failed ({}); "
                    + "companions will not break, place, open or attack where it would have decided", modId,
                    failure.getClass().getSimpleName());
        }
    }

    /** A provider that denies {@code actions} everywhere (reason {@code sibling_unavailable}) and allows the rest. */
    public static AuthorityProvider denying(Set<AuthorityAction> actions) {
        Set<AuthorityAction> denied = actions.isEmpty() ? EnumSet.noneOf(AuthorityAction.class) : EnumSet.copyOf(actions);
        return query -> denied.contains(query.action()) ? Decision.deny("sibling_unavailable") : Decision.ALLOW;
    }

    /** One INFO when a sibling integration comes on. */
    public static void enabled(String modId, String what) {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] {} found; {}.", modId, what);
    }

    /**
     * The owner of the companion an authority query is about, as an online server player, resolved by the stored
     * owner UUID (never by proximity). Null when offline or when the query has no level.
     */
    @Nullable
    public static ServerPlayer onlineOwner(AuthorityQuery query) {
        if (query.level() == null || query.owner() == null) {
            return null;
        }
        MinecraftServer server = query.level().getServer();
        return server == null ? null : server.getPlayerList().getPlayer(query.owner());
    }
}
