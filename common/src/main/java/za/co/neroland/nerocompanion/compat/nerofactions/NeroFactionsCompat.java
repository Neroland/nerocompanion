package za.co.neroland.nerocompanion.compat.nerofactions;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.authority.AuthorityAction;
import za.co.neroland.nerocompanion.authority.AuthorityQuery;
import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.compat.SiblingCompat;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * NeroFactions façade (decision 020): a membership check for {@code mayAttack} only, registered only when
 * NeroFactions is loaded. A bot never attacks a player (or another player's companion) whose owner shares a faction
 * with its own owner. NeroFactions has no claims or territory, so breaking, placing, opening and entering get no
 * objection from it; claim checks come from NeroColonies and NeroSecurity.
 *
 * <p>Privacy: membership sets are read for the two UUIDs already in hand and never stored or logged.
 */
public final class NeroFactionsCompat {

    public static final String MOD_ID = "nerofactions";

    private static volatile boolean active;

    private NeroFactionsCompat() {
    }

    public static void init() {
        init(SiblingCompat.isLoaded(MOD_ID));
    }

    static synchronized void init(boolean loaded) {
        if (!loaded || active) {
            return;
        }
        try {
            NeroFactionsBridge.probe();
            OwnerAuthority.registerProvider(NeroFactionsCompat::check);
            active = true;
            SiblingCompat.enabled(MOD_ID, "companions never attack their owner's faction");
        } catch (RuntimeException | LinkageError failure) {
            // Installed but unreachable: fail closed - no attacks on players or their companions.
            SiblingCompat.failedClosed(MOD_ID, failure, NeroFactionsCompat::failClosed);
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** The player a target answers to: itself, or a companion's stored owner. Null for anything else. */
    @Nullable
    static UUID answersTo(@Nullable Entity target) {
        if (target instanceof Player player) {
            return player.getUUID();
        }
        if (target instanceof CompanionEntity companion) {
            return companion.ownerId();
        }
        return null;
    }

    /** The provider used when NeroFactions is installed but unreachable: no attacks on anything a player owns. */
    static Decision failClosed(AuthorityQuery query) {
        if (query.action() != AuthorityAction.ATTACK) {
            return Decision.ALLOW;
        }
        UUID other = answersTo(query.target());
        return other == null || other.equals(query.owner()) ? Decision.ALLOW : Decision.deny("sibling_unavailable");
    }

    static Decision check(AuthorityQuery query) {
        if (query.action() != AuthorityAction.ATTACK || query.level() == null) {
            return Decision.ALLOW;
        }
        UUID other = answersTo(query.target());
        if (other == null || other.equals(query.owner())) {
            return Decision.ALLOW;
        }
        MinecraftServer server = query.level().getServer();
        if (server == null) {
            return Decision.deny("client");
        }
        return NeroFactionsBridge.shareFaction(server, query.owner(), other) ? Decision.deny("faction_ally")
                : Decision.ALLOW;
    }
}
