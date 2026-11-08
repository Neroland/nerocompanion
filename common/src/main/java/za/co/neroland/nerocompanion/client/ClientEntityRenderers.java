package za.co.neroland.nerocompanion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.nerocompanion.registry.ModEntities;

/**
 * The one place a companion's renderer is named; each loader's client setup passes its own {@link Sink}
 * (Fabric {@code EntityRendererRegistry}, NeoForge/Forge {@code EntityRenderersEvent.RegisterRenderers}).
 * Models are baked at construction ({@code CompanionGeometry.layer(..).bakeRoot()}), so no model-layer registry is
 * needed on any loader. Client only.
 */
public final class ClientEntityRenderers {

    /** A loader's renderer-registration entry point. */
    public interface Sink {
        <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider);
    }

    private ClientEntityRenderers() {
    }

    public static void registerAll(Sink sink) {
        // One renderer per type: its 019 model (1:1 against the hitbox), skin texture, glow and hat layers.
        sink.register(ModEntities.PERSONAL_DRONE.get(), context -> new CompanionRenderer(context, "personal_drone", 0.30F));
        sink.register(ModEntities.SCOUT_DRONE.get(), context -> new CompanionRenderer(context, "scout_drone", 0.30F));
        sink.register(ModEntities.MINING_BOT.get(), context -> new CompanionRenderer(context, "mining_bot", 0.50F));
        sink.register(ModEntities.CARRY_BOT.get(), context -> new CompanionRenderer(context, "carry_bot", 0.50F));
        sink.register(ModEntities.REPAIR_BOT.get(), context -> new CompanionRenderer(context, "repair_bot", 0.40F));
        sink.register(ModEntities.COMBAT_BOT.get(), context -> new CompanionRenderer(context, "combat_bot", 0.50F));
        sink.register(ModEntities.SCRAPLING.get(), context -> new CompanionRenderer(context, "scrapling", 0.30F));
        // The Fetch Ball flies as its item.
        sink.register(ModEntities.FETCH_BALL.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }
}
