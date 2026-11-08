package za.co.neroland.nerocompanion.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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

/** Fabric client entry point for NeroCompanion. */
public final class NeroCompanionFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroCompanionCommon.LOGGER.info("[NeroCompanion] Fabric client bootstrap");
        FabricCompanionNetwork.registerClient();
        CompanionClient.init();
        // Companion renderers through the shared client seam (Fabric's registry is the sink).
        ClientEntityRenderers.registerAll(new ClientEntityRenderers.Sink() {
            @Override
            public <E extends Entity> void register(EntityType<? extends E> type,
                    EntityRendererProvider<E> provider) {
                EntityRendererRegistry.register(type, provider);
            }
        });
        BlockEntityRendererRegistry.register(ModBlockEntities.CHARGING_PAD.get(), ctx -> new ChargingPadRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.COMPANION_BAY.get(), ctx -> new CompanionBayRenderer());
        MenuScreens.register(ModMenuTypes.COMPANION.get(), CompanionScreen::new);
        MenuScreens.register(ModMenuTypes.BAY.get(), CompanionBayScreen::new);
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "companion_hud"),
                (graphics, delta) -> CompanionHud.render(graphics));
        //?} else {
        /*HudRenderCallback.EVENT.register((graphics, delta) -> CompanionHud.render(graphics));
        *///?}
        //? if >=26.1 {
        ClientKeys.all().forEach(KeyMappingHelper::registerKeyMapping);
        //?} else {
        /*ClientKeys.all().forEach(KeyBindingHelper::registerKeyBinding);
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(mc -> CompanionClient.tick());
        // Companion particles (charge sparks, sleep z's, scout pings, hearts, scrap puffs).
        //? if >=26.1 {
        za.co.neroland.nerocompanion.client.particle.CompanionParticles.registerAll((type, factory) ->
                net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
                        .register(type, sprites -> factory.apply(sprites)));
        //?} else {
        /*za.co.neroland.nerocompanion.client.particle.CompanionParticles.registerAll((type, factory) ->
                net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
                        .register(type, sprites -> factory.apply(sprites)));
        *///?}
        // Client-side /nerocompanion_capture tree (its own root: sharing "nerocompanion" would shadow the server gallery).
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) ->
                        za.co.neroland.nerocompanion.client.GalleryCaptureHarness.registerClientCommands(dispatcher));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.clear());
    }
}
