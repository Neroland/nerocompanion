package za.co.neroland.nerocompanion.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.charge.ChargingPadBlockEntity;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * Block entity types. Built through Core's {@link BlockEntityTypeCompat} (1.21.1 takes a data-fixer argument, and
 * 26.1.2 made the constructor private - Core widens it), the family's shared workaround.
 */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> TYPES =
            Services.REGISTRATION.provider(Registries.BLOCK_ENTITY_TYPE, NeroCompanionCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<ChargingPadBlockEntity>> CHARGING_PAD = TYPES.register(
            "charging_pad", key -> BlockEntityTypeCompat.create(ChargingPadBlockEntity::new,
                    ModBlocks.CHARGING_PAD.get()));
    public static final RegistryEntry<BlockEntityType<CompanionBayBlockEntity>> COMPANION_BAY = TYPES.register(
            "companion_bay", key -> BlockEntityTypeCompat.create(CompanionBayBlockEntity::new,
                    ModBlocks.COMPANION_BAY.get()));

    private ModBlockEntities() {
    }

    public static void init() {
    }
}
