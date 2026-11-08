package za.co.neroland.nerocompanion.neoforge;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

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
 * NeoForge client-only wiring: entity and block-entity renderers, the screens, the HUD strip, key mappings, the
 * client tick and the disconnect reset. The entry point calls {@link #init} only on the client dist.
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        CompanionClient.init();
        modEventBus.addListener(NeoForgeClientSetup::onRegisterEntityRenderers);
        modEventBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(ModMenuTypes.COMPANION.get(), CompanionScreen::new);
            event.register(ModMenuTypes.BAY.get(), CompanionBayScreen::new);
        });
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "companion_hud"),
                (g, delta) -> CompanionHud.render(g)));
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> ClientKeys.all().forEach(event::register));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> CompanionClient.tick());
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
                za.co.neroland.nerocompanion.client.particle.CompanionParticles.registerAll((type, factory) ->
                        event.registerSpriteSet(type, factory::apply)));
        // Client-side /nscompgallery capture tree (its own root: a shared "nscomp" root would shadow the server's).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) ->
                za.co.neroland.nerocompanion.client.GalleryCaptureHarness.registerClientCommands(
                        event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientState.clear());
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
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
