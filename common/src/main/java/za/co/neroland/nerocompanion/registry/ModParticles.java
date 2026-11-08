package za.co.neroland.nerocompanion.registry;

import java.util.List;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The five companion particles ({@code 010} section 5.3): drawn from {@code assets/nerocompanion/particles/<id>.json}
 * and {@code textures/particle/<id>_<n>.png}; each loader registers the client providers through
 * {@code client.particle.CompanionParticles}. Spawned by the server ({@code sendParticles}) for one-shots everyone
 * should see (hatch puffs) and by the client effects pump for per-frame flourishes (charge sparks, sleep z's).
 */
public final class ModParticles {

    public static final RegistrationProvider<ParticleType<?>> PARTICLES =
            Services.REGISTRATION.provider(Registries.PARTICLE_TYPE, NeroCompanionCommon.MOD_ID);

    public static final RegistryEntry<SimpleParticleType> CHARGE_SPARK = register("charge_spark");
    public static final RegistryEntry<SimpleParticleType> SLEEP_Z = register("sleep_z");
    public static final RegistryEntry<SimpleParticleType> SCOUT_PING = register("scout_ping");
    public static final RegistryEntry<SimpleParticleType> BOND_HEART = register("bond_heart");
    public static final RegistryEntry<SimpleParticleType> SCRAP_PUFF = register("scrap_puff");

    /** Every particle id path, in registration order (the asset checker reads it). */
    public static final List<String> IDS = List.of("charge_spark", "sleep_z", "scout_ping", "bond_heart", "scrap_puff");

    private static RegistryEntry<SimpleParticleType> register(String path) {
        // The constructor is protected outside NeoForge's access transformer: an empty subclass works everywhere.
        return PARTICLES.register(path, key -> new SimpleParticleType(false) {
        });
    }

    private ModParticles() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
