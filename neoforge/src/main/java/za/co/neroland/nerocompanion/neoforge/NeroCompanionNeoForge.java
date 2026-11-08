package za.co.neroland.nerocompanion.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.budget.BudgetProbe;
import za.co.neroland.nerocompanion.command.CompanionCommands;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/** NeoForge entry point for NeroCompanion. */
@Mod(NeroCompanionCommon.MOD_ID)
public final class NeroCompanionNeoForge {

    public NeroCompanionNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] NeoForge bootstrap");
        NeroCompanionCommon.init();
        Services.REGISTRATION.attach(modEventBus);
        // Core's entity-attribute plumbing on our mod bus (idempotent; Core also installs its own).
        Services.ENTITIES.attach(modEventBus);
        NeoForgeCompanionNetwork.register(modEventBus);
        NeoForgeCompanionCapabilities.register(modEventBus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                NeroCompanionCommon.onPlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> CompanionCommands.register(event.getDispatcher()));
        // Quest hook delivery (skin unlock notice) needs the running server; captured from the server tick.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            QuestHooks.tick(event.getServer());
            NeroCompanionCommon.onServerTick(event.getServer());
            BudgetProbe.endServerTick();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                NeroCompanionCommon.onServerStopped());
        // Client-only: renderers. Gated so the client classes never load on a dedicated server.
        //? if >=1.21.9 {
        boolean client = FMLEnvironment.getDist() == Dist.CLIENT;
        //?} else {
        /*boolean client = FMLEnvironment.dist == Dist.CLIENT;
        *///?}
        if (client) {
            NeoForgeClientSetup.init(modEventBus);
        }
    }
}
