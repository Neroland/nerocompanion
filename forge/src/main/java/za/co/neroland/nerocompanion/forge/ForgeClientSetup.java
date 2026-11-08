package za.co.neroland.nerocompanion.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
*///?}

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.client.ClientEntityRenderers;
import za.co.neroland.nerocompanion.client.ClientKeys;
import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.CompanionClient;
import za.co.neroland.nerocompanion.client.hud.CompanionHud;
import za.co.neroland.nerocompanion.client.render.ChargingPadRenderer;
import za.co.neroland.nerocompanion.client.render.CompanionBayRenderer;
import za.co.neroland.nerocompanion.client.screen.CompanionBayScreen;
import za.co.neroland.nerocompanion.client.screen.CompanionScreen;
import za.co.neroland.nerocompanion.registry.ModBlockEntities;
import za.co.neroland.nerocompanion.registry.ModMenuTypes;

/**
 * Forge client-only wiring: renderers, screens, the HUD strip, key mappings, the client tick and the disconnect
 * reset; the entry point calls {@code init} only on the client dist. From 1.21.9 each event has its own static bus;
 * 1.21.6-1.21.8 take the mod-bus events from the mod bus group; before that from the mod bus.
 */
public final class ForgeClientSetup {

    private static final Identifier HUD = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "companion_hud");

    private ForgeClientSetup() {
    }

    private static void screens() {
        MenuScreens.register(ModMenuTypes.COMPANION.get(), CompanionScreen::new);
        MenuScreens.register(ModMenuTypes.BAY.get(), CompanionBayScreen::new);
    }

    //? if >=1.21.9 {
    public static void init(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        CompanionClient.init();
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterRenderers);
        FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(ForgeClientSetup::screens));
        AddGuiOverlayLayersEvent.BUS.addListener(event ->
                event.getLayeredDraw().add(HUD, (g, delta) -> CompanionHud.render(g)));
        RegisterKeyMappingsEvent.BUS.addListener(event -> ClientKeys.all().forEach(event::register));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> CompanionClient.tick());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientState.clear());
        net.minecraftforge.client.event.RegisterParticleProvidersEvent.BUS.addListener(ForgeClientSetup::particles);
        net.minecraftforge.client.event.RegisterClientCommandsEvent.BUS.addListener(event ->
                za.co.neroland.nerocompanion.client.GalleryCaptureHarness.registerClientCommands(event.getDispatcher()));
    }
    //?} else if >=1.21.6 {
    /*public static void init(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        CompanionClient.init();
        EntityRenderersEvent.RegisterRenderers.getBus(modBus).addListener(ForgeClientSetup::onRegisterRenderers);
        FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(ForgeClientSetup::screens));
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(event ->
                event.getLayeredDraw().add(HUD, (g, delta) -> CompanionHud.render(g)));
        RegisterKeyMappingsEvent.getBus(modBus).addListener(event -> ClientKeys.all().forEach(event::register));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> CompanionClient.tick());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientState.clear());
        net.minecraftforge.client.event.RegisterParticleProvidersEvent.getBus(modBus).addListener(ForgeClientSetup::particles);
        net.minecraftforge.client.event.RegisterClientCommandsEvent.BUS.addListener(event ->
                za.co.neroland.nerocompanion.client.GalleryCaptureHarness.registerClientCommands(event.getDispatcher()));
    }
    *///?} else {
    /*public static void init(net.minecraftforge.eventbus.api.IEventBus modBus) {
        CompanionClient.init();
        modBus.addListener(ForgeClientSetup::onRegisterRenderers);
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ForgeClientSetup::screens));
        modBus.addListener((AddGuiOverlayLayersEvent event) ->
                event.getLayeredDraw().add(HUD, (g, delta) -> CompanionHud.render(g)));
        modBus.addListener((RegisterKeyMappingsEvent event) -> ClientKeys.all().forEach(event::register));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> CompanionClient.tick());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientState.clear());
        modBus.addListener(ForgeClientSetup::particles);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RegisterClientCommandsEvent event) ->
                za.co.neroland.nerocompanion.client.GalleryCaptureHarness.registerClientCommands(event.getDispatcher()));
    }
    *///?}

    private static void particles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        za.co.neroland.nerocompanion.client.particle.CompanionParticles.registerAll((type, factory) ->
                event.registerSpriteSet(type, factory::apply));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientEntityRenderers.registerAll(new ClientEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type,
                    EntityRendererProvider<E> provider) {
                event.registerEntityRenderer(type, provider);
            }
        });
        event.registerBlockEntityRenderer(ModBlockEntities.CHARGING_PAD.get(), ctx -> new ChargingPadRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.COMPANION_BAY.get(), ctx -> new CompanionBayRenderer());
    }
}
