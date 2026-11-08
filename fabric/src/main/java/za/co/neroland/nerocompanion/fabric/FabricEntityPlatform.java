package za.co.neroland.nerocompanion.fabric;

import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerocompanion.platform.EntityPlatform;

/** Fabric side of the entity seam. Registered via {@code META-INF/services}. */
public final class FabricEntityPlatform implements EntityPlatform {

    @Override
    public void attach(Object modBus) {
        // Fabric registers default attributes the moment they are declared: nothing to attach.
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof net.fabricmc.fabric.api.entity.FakePlayer || EntityPlatform.notTheListedPlayer(player);
    }
}
