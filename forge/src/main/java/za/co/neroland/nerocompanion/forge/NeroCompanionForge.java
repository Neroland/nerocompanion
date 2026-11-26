package za.co.neroland.nerocompanion.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.budget.BudgetProbe;
import za.co.neroland.nerocompanion.command.CompanionCommands;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/** MinecraftForge entry point for NeroCompanion. */
@Mod(NeroCompanionCommon.MOD_ID)
public final class NeroCompanionForge {

    //? if >=1.20.5 {
    public NeroCompanionForge(FMLJavaModLoadingContext context) {
    //?} else {
    /*public NeroCompanionForge() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
    *///?}
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Forge bootstrap");
        NeroCompanionCommon.init();
        //? if >=1.21.6 {
        Services.REGISTRATION.attach(context.getModBusGroup());
        Services.ENTITIES.attach(context.getModBusGroup());
        //?} else {
        /*Services.REGISTRATION.attach(context.getModEventBus());
        Services.ENTITIES.attach(context.getModEventBus());
        *///?}
        // Client-only: renderers. Gated so the client classes never load on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            //? if >=1.21.6 {
            ForgeClientSetup.init(context.getModBusGroup());
            //?} else {
            /*ForgeClientSetup.init(context.getModEventBus());
            *///?}
        }
        ForgeCompanionNetwork.register();
        ForgeCompanionCapabilities.register();
        // Quest hook delivery (skin unlock notice) needs the running server; captured from the server tick.
        //? if >=1.21.9 {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            QuestHooks.tick(event.server());
            NeroCompanionCommon.onServerTick(event.server());
            BudgetProbe.endServerTick();
        });
        net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                NeroCompanionCommon.onPlayerJoin(player);
            }
        });
        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener(
                event -> CompanionCommands.register(event.getDispatcher()));
        net.minecraftforge.event.server.ServerStoppedEvent.BUS.addListener(
                event -> NeroCompanionCommon.onServerStopped());
        //?} else if >=1.21.6 {
        /*TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            QuestHooks.tick(event.getServer());
            NeroCompanionCommon.onServerTick(event.getServer());
            BudgetProbe.endServerTick();
        });
        net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                NeroCompanionCommon.onPlayerJoin(player);
            }
        });
        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener(
                event -> CompanionCommands.register(event.getDispatcher()));
        net.minecraftforge.event.server.ServerStoppedEvent.BUS.addListener(
                event -> NeroCompanionCommon.onServerStopped());
        *///?} else {
        /*// Forge before 1.21.6 runs EventBus 6: one game bus, listeners typed by their parameter.
        registerServerTick();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        NeroCompanionCommon.onPlayerJoin(player);
                    }
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent event) -> CompanionCommands.register(event.getDispatcher()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppedEvent event) -> NeroCompanionCommon.onServerStopped());
        *///?}
    }
    //? if <1.21.6 {
    /*private static void registerServerTick() {
        //? if >=1.20.5 {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> tick(event.getServer()));
        //?} else {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                tick(event.getServer());
            }
        });
        //?}
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        QuestHooks.tick(server);
        NeroCompanionCommon.onServerTick(server);
        BudgetProbe.endServerTick();
    }
    *///?}
}
