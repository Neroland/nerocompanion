package za.co.neroland.nerocompanion.compat.nerocolonies;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.compat.SiblingCompat;

/**
 * NeroColonies façade (decision 020): an owner-authority provider, registered only when NeroColonies is loaded.
 * A bot may break, place, open, enter or attack at a position only where its owner could build
 * ({@code ColonyApi.canBuild}). The provider can only deny; a failure inside it denies (decision 002). Installed
 * but unreachable (the probe fails): a deny-all provider for break/place/open/attack is registered instead.
 */
public final class NeroColoniesCompat {

    public static final String MOD_ID = "nerocolonies";

    private static volatile boolean active;

    private NeroColoniesCompat() {
    }

    public static void init() {
        init(SiblingCompat.isLoaded(MOD_ID));
    }

    static synchronized void init(boolean loaded) {
        if (!loaded || active) {
            return;
        }
        try {
            NeroColoniesBridge.probe();
            OwnerAuthority.registerProvider(NeroColoniesCompat::check);
            active = true;
            SiblingCompat.enabled(MOD_ID, "companions respect colony claims");
        } catch (RuntimeException | LinkageError failure) {
            // Installed but unreachable: fail closed for claims (following still works).
            SiblingCompat.failedClosed(MOD_ID, failure, SiblingCompat.denying(java.util.EnumSet.of(
                    AuthorityAction.BREAK, AuthorityAction.PLACE, AuthorityAction.OPEN, AuthorityAction.ATTACK)));
        }
    }

    public static boolean isActive() {
        return active;
    }

    static Decision check(AuthorityQuery query) {
        if (query.level() == null || query.pos() == null) {
            return Decision.ALLOW;
        }
        ServerPlayer owner = SiblingCompat.onlineOwner(query);
        if (owner == null) {
            return Decision.deny("owner_offline");
        }
        return NeroColoniesBridge.canBuild(owner, query.level(), query.pos()) ? Decision.ALLOW
                : Decision.deny("colony");
    }
}
