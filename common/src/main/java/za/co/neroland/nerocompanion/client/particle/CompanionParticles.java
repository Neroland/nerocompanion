package za.co.neroland.nerocompanion.client.particle;

import java.util.function.Function;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

import za.co.neroland.nerocompanion.registry.ModParticles;

/**
 * Cross-loader particle-provider wiring: the provider set lives here once and each loader passes its own
 * registration ({@link Sink}): NeoForge and Forge {@code RegisterParticleProvidersEvent.registerSpriteSet}, Fabric
 * {@code ParticleProviderRegistry} (26.1+) / {@code ParticleFactoryRegistry} (older lines). Client only.
 */
public final class CompanionParticles {

    /** A loader's sprite-set provider registration. */
    public interface Sink {
        void register(SimpleParticleType type, Function<SpriteSet, ParticleProvider<SimpleParticleType>> factory);
    }

    public static void registerAll(Sink sink) {
        sink.register(ModParticles.CHARGE_SPARK.get(),
                s -> new CompanionParticle.Provider(s, CompanionParticle.Kind.CHARGE_SPARK));
        sink.register(ModParticles.SLEEP_Z.get(), s -> new CompanionParticle.Provider(s, CompanionParticle.Kind.SLEEP_Z));
        sink.register(ModParticles.SCOUT_PING.get(),
                s -> new CompanionParticle.Provider(s, CompanionParticle.Kind.SCOUT_PING));
        sink.register(ModParticles.BOND_HEART.get(),
                s -> new CompanionParticle.Provider(s, CompanionParticle.Kind.BOND_HEART));
        sink.register(ModParticles.SCRAP_PUFF.get(),
                s -> new CompanionParticle.Provider(s, CompanionParticle.Kind.SCRAP_PUFF));
    }

    private CompanionParticles() {
    }
}
