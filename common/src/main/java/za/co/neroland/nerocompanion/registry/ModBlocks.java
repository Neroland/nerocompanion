package za.co.neroland.nerocompanion.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.PropertiesCompat;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.charge.ChargingPadBlock;
import za.co.neroland.nerocompanion.charge.CompanionBayBlock;
import za.co.neroland.nerocompanion.charge.HandCrankChargerBlock;
import za.co.neroland.nerocompanion.platform.Services;

/** NeroCompanion's blocks: the three chargers. */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            Services.REGISTRATION.provider(Registries.BLOCK, NeroCompanionCommon.MOD_ID);

    public static final RegistryEntry<ChargingPadBlock> CHARGING_PAD = BLOCKS.register("charging_pad",
            key -> new ChargingPadBlock(PropertiesCompat.block(key).mapColor(MapColor.METAL).strength(2.5F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> state.getValue(ChargingPadBlock.CHARGING)
                            ? 7 : 0)));
    public static final RegistryEntry<CompanionBayBlock> COMPANION_BAY = BLOCKS.register("companion_bay",
            key -> new CompanionBayBlock(PropertiesCompat.block(key).mapColor(MapColor.METAL).strength(3.5F)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final RegistryEntry<HandCrankChargerBlock> HAND_CRANK_CHARGER = BLOCKS.register(
            "hand_crank_charger", key -> new HandCrankChargerBlock(PropertiesCompat.block(key).mapColor(MapColor.METAL)
                    .strength(2.0F).sound(SoundType.METAL).noOcclusion()));

    private ModBlocks() {
    }

    public static void init() {
    }
}
