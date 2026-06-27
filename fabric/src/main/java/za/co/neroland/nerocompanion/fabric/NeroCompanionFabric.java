package za.co.neroland.nerocompanion.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/** Fabric entry point for NeroCompanion. */
public final class NeroCompanionFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Fabric bootstrap");
        NeroCompanionCommon.init();
    }
}
