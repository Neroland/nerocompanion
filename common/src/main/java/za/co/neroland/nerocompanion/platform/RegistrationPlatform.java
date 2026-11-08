package za.co.neroland.nerocompanion.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

public interface RegistrationPlatform {
    <T> RegistrationProvider<T> provider(ResourceKey<? extends Registry<T>> registry, String modId);
    void attach(Object modBus);
}
