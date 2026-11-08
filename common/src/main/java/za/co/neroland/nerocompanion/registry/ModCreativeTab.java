package za.co.neroland.nerocompanion.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * NeroCompanion's own creative tab (id {@code nerocompanion}). Items are never added to Core's tab.
 * Order: capsules (empty, then one crafted blueprint per type), the Scrap Egg, the Remote, the chargers, the Fetch
 * Ball, chips, and last the creative-only spawn eggs.
 */
public final class ModCreativeTab {
    private static final RegistrationProvider<CreativeModeTab> TABS =
            Services.REGISTRATION.provider(Registries.CREATIVE_MODE_TAB, NeroCompanionCommon.MOD_ID);

    public static final RegistrationProvider.RegistryEntry<CreativeModeTab> TAB = TABS.register("nerocompanion",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.nerocompanion"))
                    .icon(() -> new ItemStack(ModItems.CAPSULE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CAPSULE.get());
                        // The crafted companions (a Capsule blueprint per type; the Scrapling hatches from its egg).
                        for (net.minecraft.resources.Identifier type : ModEntities.COMPANION_TYPES) {
                            if (!type.getPath().equals("scrapling")) {
                                ItemStack crafted = new ItemStack(ModItems.CAPSULE.get());
                                crafted.set(ModDataComponents.BLUEPRINT.get(), type);
                                output.accept(crafted);
                            }
                        }
                        output.accept(ModItems.SCRAP_EGG.get());
                        output.accept(ModItems.REMOTE.get());
                        output.accept(ModItems.CHARGING_PAD.get());
                        output.accept(ModItems.COMPANION_BAY.get());
                        output.accept(ModItems.HAND_CRANK_CHARGER.get());
                        output.accept(ModItems.FETCH_BALL.get());
                        output.accept(ModItems.BLANK_CHIP.get());
                        ModItems.chipsInOrder().forEach(chip -> output.accept(chip.get()));
                        ModItems.lookItems().forEach(item -> output.accept(item.get()));
                        ModItems.spawnEggs().forEach(egg -> output.accept(egg.get()));
                    })
                    .build());

    private ModCreativeTab() { }

    public static void init() { }
}
