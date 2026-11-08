package za.co.neroland.nerocompanion.compat.nerofactions;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.membership.FactionMembership;

/** The <b>only</b> class that names a NeroFactions type; loaded only from {@link NeroFactionsCompat}. */
final class NeroFactionsBridge {

    private NeroFactionsBridge() {
    }

    static void probe() {
        FactionMembership.class.getName();
    }

    static boolean shareFaction(MinecraftServer server, UUID a, UUID b) {
        Set<?> mine = FactionMembership.membershipOf(server, a);
        if (mine == null || mine.isEmpty()) {
            return false;
        }
        Set<?> theirs = FactionMembership.membershipOf(server, b);
        return theirs != null && !Collections.disjoint(mine, theirs);
    }
}
