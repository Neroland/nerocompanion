package za.co.neroland.nerocompanion.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/** NeoForge entry point for NeroCompanion. */
@Mod(NeroCompanionCommon.MOD_ID)
public final class NeroCompanionNeoForge {

    public NeroCompanionNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] NeoForge bootstrap");
        NeroCompanionCommon.init();
        // Quest hook delivery (skin unlock notice) needs the running server; captured from the server tick.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> QuestHooks.tick(event.getServer()));
    }
}
