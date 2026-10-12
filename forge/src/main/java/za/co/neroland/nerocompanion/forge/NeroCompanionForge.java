package za.co.neroland.nerocompanion.forge;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/** MinecraftForge entry point for NeroCompanion. */
@Mod(NeroCompanionCommon.MOD_ID)
public final class NeroCompanionForge {

    public NeroCompanionForge(FMLJavaModLoadingContext context) {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Forge bootstrap");
        NeroCompanionCommon.init();
        // Quest hook delivery (skin unlock notice) needs the running server; captured from the server tick.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> QuestHooks.tick(event.server()));
    }
}
