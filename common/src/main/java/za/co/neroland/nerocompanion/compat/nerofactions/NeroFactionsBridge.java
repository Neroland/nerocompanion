package za.co.neroland.nerocompanion.compat.nerofactions;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.api.NeroFactionsApi;

/**
 * The <b>only</b> class that names a NeroFactions type; loaded only from {@link NeroFactionsCompat}. It talks to
 * NeroFactions' public {@link NeroFactionsApi} (0.7.0-beta.1 and later) only. An older NeroFactions without that API
 * fails {@link #probe()} with a {@link LinkageError}, which the façade treats as installed but unreachable.
 */
final class NeroFactionsBridge {

    private NeroFactionsBridge() {
    }

    static void probe() {
        NeroFactionsApi.class.getName();
    }

    static boolean shareFaction(MinecraftServer server, UUID a, UUID b) {
        // The API answers an empty set when membership is unavailable, so "unknown" never reads as "allied".
        Set<?> mine = NeroFactionsApi.membershipOf(server, a);
        if (mine == null || mine.isEmpty()) {
            return false;
        }
        return FactionOverlap.shareFaction(mine, NeroFactionsApi.membershipOf(server, b));
    }
}
