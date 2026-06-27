package za.co.neroland.nerocompanion.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/** NeoForge entry point for NeroCompanion. */
@Mod(NeroCompanionCommon.MOD_ID)
public final class NeroCompanionNeoForge {

    public NeroCompanionNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] NeoForge bootstrap");
        NeroCompanionCommon.init();
    }
}
