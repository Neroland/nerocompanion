package za.co.neroland.nerocompanion.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/** Fabric client entry point for NeroCompanion. */
public final class NeroCompanionFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Fabric client bootstrap");
    }
}
