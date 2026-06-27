package za.co.neroland.nerocompanion.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/** MinecraftForge entry point for NeroCompanion. */
@Mod(NeroCompanionCommon.MOD_ID)
public final class NeroCompanionForge {

    public NeroCompanionForge(FMLJavaModLoadingContext context) {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Forge bootstrap");
        NeroCompanionCommon.init();
    }
}
