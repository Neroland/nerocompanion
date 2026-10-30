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
        //? if >=1.21.9 {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> QuestHooks.tick(event.server()));
        //?} else if >=1.21.6 {
        /*TickEvent.ServerTickEvent.Post.BUS.addListener(event -> QuestHooks.tick(event.getServer()));
        *///?} else {
        /*// Forge before 1.21.6 runs EventBus 6: one game bus, listeners typed by their parameter.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (TickEvent.ServerTickEvent.Post event) -> QuestHooks.tick(event.getServer()));
        *///?}
    }
}
