package za.co.neroland.nerocompanion.platform;

import java.util.ServiceLoader;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * Loads loader-specific platform-seam implementations via {@link ServiceLoader}.
 *
 * <p>Common code calls {@code Services.PLATFORM.xxx()}; the correct Fabric / Forge / NeoForge
 * implementation is resolved at runtime from the {@code META-INF/services} entry each loader module
 * ships. The seam is resolved during class initialisation, which happens the first time
 * {@code NeroCompanionCommon.init()} touches this class — <b>never lazily mid-tick</b>. A lazy
 * {@link ServiceLoader} read can throw {@code ServiceConfigurationError} out of gameplay code if
 * the jar has become unreadable (Nerospace crash precedent MC-NEROSPACE-F).
 */
public final class Services {

    public static final PlatformInfo PLATFORM = load(PlatformInfo.class);
    public static final NetworkPlatform NETWORK = load(NetworkPlatform.class);
    public static final RegistrationPlatform REGISTRATION = load(RegistrationPlatform.class);
    public static final EntityPlatform ENTITIES = load(EntityPlatform.class);

    private Services() {
    }

    /** Resolves the seam now, so nothing has to be resolved on a tick path later. */
    public static void init() {
        NeroCompanionCommon.LOGGER.debug("[NeroCompanion] platform seams resolved ({})",
                PLATFORM.getPlatformName());
    }

    public static <T> T load(Class<T> clazz) {
        final T loaded = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException(
                        "No implementation found for service " + clazz.getName()));
        NeroCompanionCommon.LOGGER.debug("Loaded service {} -> {}",
                clazz.getSimpleName(), loaded.getClass().getName());
        return loaded;
    }
}
