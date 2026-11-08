package za.co.neroland.nerocompanion.forge;

import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerocompanion.platform.EntityPlatform;

/** Forge side of the entity seam. Registered via {@code META-INF/services}. */
public final class ForgeEntityPlatform implements EntityPlatform {

    @Override
    public void attach(Object modBus) {
        EntityRegistrationSupport.attach(modBus);
    }

    @Override
    public boolean isFakePlayer(Player player) {
        // Forge's FakePlayer class is not on every line (gone from the 26.x userdev), so use the loader-agnostic
        // test: a real player is the one the server's player list holds under that UUID; a fake one never is.
        return EntityPlatform.notTheListedPlayer(player);
    }
}
