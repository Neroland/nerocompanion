package za.co.neroland.nerocompanion.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/** Fabric entry point for NeroCompanion. */
public final class NeroCompanionFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Fabric bootstrap");
        NeroCompanionCommon.init();
        // Quest hook delivery (skin unlock notice) needs the running server; captured from the server tick.
        ServerTickEvents.END_SERVER_TICK.register(QuestHooks::tick);
    }
}
