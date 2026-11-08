package za.co.neroland.nerocompanion.compat.nerosecurity;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.compat.SiblingCompat;

/**
 * NeroSecurity façade (decision 020): an owner-authority provider, registered only when NeroSecurity is loaded. A
 * secured device (a block an installation covers) may be opened, broken or entered by a bot only when its owner
 * passes that installation's access check for {@code OPEN}. NeroSecurity has no public API package, so the bridge
 * binds to its internal access classes; any mismatch is a {@code LinkageError}, which denies (fails closed).
 */
public final class NeroSecurityCompat {

    public static final String MOD_ID = "nerosecurity";

    private static volatile boolean active;

    private NeroSecurityCompat() {
    }

    public static void init() {
        init(SiblingCompat.isLoaded(MOD_ID));
    }

    static synchronized void init(boolean loaded) {
        if (!loaded || active) {
            return;
        }
        try {
            NeroSecurityBridge.probe();
            OwnerAuthority.registerProvider(NeroSecurityCompat::check);
            active = true;
            SiblingCompat.enabled(MOD_ID, "companions respect secured devices");
        } catch (RuntimeException | LinkageError failure) {
            // Installed but unreachable: fail closed for secured devices (following still works).
            SiblingCompat.failedClosed(MOD_ID, failure, SiblingCompat.denying(java.util.EnumSet.of(
                    AuthorityAction.BREAK, AuthorityAction.OPEN)));
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** The actions a secured device restricts. */
    static boolean restricts(AuthorityAction action) {
        return action == AuthorityAction.OPEN || action == AuthorityAction.BREAK || action == AuthorityAction.ENTER;
    }

    static Decision check(AuthorityQuery query) {
        if (!restricts(query.action()) || query.level() == null || query.pos() == null) {
            return Decision.ALLOW;
        }
        ServerPlayer owner = SiblingCompat.onlineOwner(query);
        if (owner == null) {
            return Decision.deny("owner_offline");
        }
        return NeroSecurityBridge.allowed(owner, query.level(), query.pos()) ? Decision.ALLOW
                : Decision.deny("security");
    }
}
