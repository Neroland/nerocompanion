package za.co.neroland.nerocompanion.neoforge;

import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerocompanion.platform.EntityPlatform;

/** NeoForge side of the entity seam. Registered via {@code META-INF/services}. */
public final class NeoForgeEntityPlatform implements EntityPlatform {

    @Override
    public void attach(Object modBus) {
        EntityRegistrationSupport.attach(modBus);
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof net.neoforged.neoforge.common.util.FakePlayer || EntityPlatform.notTheListedPlayer(player);
    }
}
