package za.co.neroland.nerocompanion.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.menu.BayMenu;
import za.co.neroland.nerocompanion.menu.CompanionMenu;
import za.co.neroland.nerocompanion.platform.Services;

/** Menu types (no slots; the screens draw from owner-only snapshots). */
public final class ModMenuTypes {

    public static final RegistrationProvider<MenuType<?>> TYPES =
            Services.REGISTRATION.provider(Registries.MENU, NeroCompanionCommon.MOD_ID);

    public static final RegistryEntry<MenuType<CompanionMenu>> COMPANION = TYPES.register("companion",
            key -> new MenuType<>(CompanionMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<BayMenu>> BAY = TYPES.register("companion_bay",
            key -> new MenuType<>(BayMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenuTypes() {
    }

    public static void init() {
    }
}
