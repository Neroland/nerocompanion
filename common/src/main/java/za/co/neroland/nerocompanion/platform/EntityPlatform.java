package za.co.neroland.nerocompanion.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Loader seam for the entity pieces that differ per loader. Default attributes go through Core's
 * {@code EntityRegistrationSupport} (common code); renderers are registered by each loader's client setup
 * through {@code client/ClientEntityRenderers}; spawn eggs are plain {@code Item}s. What remains loader-specific
 * lives here.
 */
public interface EntityPlatform {

    /**
     * Attaches Core's entity-attribute plumbing to the mod bus (NeoForge/Forge; a no-op on Fabric, which
     * registers attributes immediately). Called by the loader entry point right after common init.
     */
    void attach(Object modBus);

    /**
     * Whether {@code player} is a loader FakePlayer (a machine acting as a player). A FakePlayer is never a
     * companion owner and never lends authority.
     */
    boolean isFakePlayer(Player player);

    /**
     * The loader-agnostic fake-player test every implementation also applies: a real server player is the one
     * the server's player list holds under its UUID. Catches automation mods whose fake players do not extend the
     * loader's {@code FakePlayer} class.
     */
    static boolean notTheListedPlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        MinecraftServer server = serverPlayer.level().getServer();
        return server == null || server.getPlayerList().getPlayer(serverPlayer.getUUID()) != serverPlayer;
    }
}
