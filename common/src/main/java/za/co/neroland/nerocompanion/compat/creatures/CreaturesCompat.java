package za.co.neroland.nerocompanion.compat.creatures;

import za.co.neroland.nerocompanion.platform.Services;

/**
 * NeroCreatures detection. NeroCreatures is optional and the direction is reversed from the other façades:
 * NeroCompanion implements NeroCreatures' SPI ({@link CompanionPetHooksImpl}, found by NeroCreatures through
 * {@code ServiceLoader}) instead of calling into it. This class never names a NeroCreatures type; callers that
 * want the pet list read {@link CreaturePets}.
 */
public final class CreaturesCompat {

    public static final String MOD_ID = "nerocreatures";

    /** The implementation NeroCreatures loads, by name only (never as a class literal). */
    public static final String HOOKS_IMPL = "za.co.neroland.nerocompanion.compat.creatures.CompanionPetHooksImpl";

    private CreaturesCompat() {
    }

    /** True when NeroCreatures is installed. */
    public static boolean isLoaded() {
        try {
            return Services.PLATFORM.isModLoaded(MOD_ID);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
