package za.co.neroland.nerocompanion.authority;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.protection.ClaimChecks;

/**
 * Neroland Core's land-protection SPI as an authority step: before a bot breaks, places or opens a block for its owner,
 * every claims mod registered with Core's {@code ClaimChecks} must let the owner interact there. With no claims mod
 * installed {@code ClaimChecks.active()} is false and the step is skipped entirely, so a Core-only server answers
 * exactly as before. Nothing is stored; the owner is resolved by the stored owner UUID, never by proximity.
 */
public final class CoreClaimsAuthority {

    private CoreClaimsAuthority() {
    }

    /** The actions a land claim restricts. */
    static boolean restricts(AuthorityAction action) {
        return action == AuthorityAction.BREAK || action == AuthorityAction.PLACE || action == AuthorityAction.OPEN;
    }

    public static Decision check(AuthorityQuery query) {
        if (!restricts(query.action()) || query.level() == null || query.pos() == null || query.owner() == null) {
            return Decision.ALLOW;
        }
        MinecraftServer server = query.level().getServer();
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(query.owner());
        if (owner == null) {
            return Decision.deny("owner_offline");
        }
        return ClaimChecks.mayInteract(owner, query.level(), query.pos()) ? Decision.ALLOW : Decision.deny("claim");
    }
}
