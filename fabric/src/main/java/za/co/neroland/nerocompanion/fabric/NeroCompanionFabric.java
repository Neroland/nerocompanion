package za.co.neroland.nerocompanion.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import za.co.neroland.nerolandcore.platform.FabricEnergyLookup;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.registry.ModBlockEntities;
import za.co.neroland.nerocompanion.budget.BudgetProbe;
import za.co.neroland.nerocompanion.command.CompanionCommands;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/** Fabric entry point for NeroCompanion. */
public final class NeroCompanionFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Fabric bootstrap");
        NeroCompanionCommon.init();
        FabricCompanionNetwork.registerCommon();
        // Chargers on Core's shared energy lookup, gated per face by their side configuration.
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig() != null
                ? be.sideConfig().energyView(dir) : be.getEnergy(), ModBlockEntities.CHARGING_PAD.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig() != null
                ? be.sideConfig().energyView(dir) : be.getEnergy(), ModBlockEntities.COMPANION_BAY.get());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                NeroCompanionCommon.onPlayerJoin(handler.player));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> CompanionCommands.register(dispatcher));
        // Quest hook delivery (skin unlock notice) needs the running server; captured from the server tick.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            QuestHooks.tick(server);
            NeroCompanionCommon.onServerTick(server);
            BudgetProbe.endServerTick();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> NeroCompanionCommon.onServerStopped());
    }
}
