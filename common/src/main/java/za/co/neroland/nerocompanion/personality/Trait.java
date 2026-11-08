package za.co.neroland.nerocompanion.personality;

import java.util.Locale;

/**
 * The six personality traits. A companion's trait is fixed by its personality seed (rolled once at first deploy and
 * kept in the record), so the same companion always has the same personality, across recalls, parking and
 * servers. Traits change idle habits, chatter lines and the bubble colour only - never stats.
 *
 * <p>The index is the synched {@code DATA_TRAIT} byte and must match {@code CompanionEntity#trait()}
 * ({@code floorMod(seed, 6)}); the order below is therefore part of the save format.
 */
public enum Trait {
    CURIOUS(0xFF56CCC4),
    LOYAL(0xFF58E07A),
    GRUMPY(0xFFFFB347),
    SLEEPY(0xFFB48CFF),
    SHOWOFF(0xFFFF8CCF),
    SHY(0xFF7FD7FF);

    /** Number of traits (= {@code CompanionEntity.TRAITS}). */
    public static final int COUNT = 6;

    private static final Trait[] VALUES = values();

    private final int colour;
    private final String id;

    Trait(int colour) {
        this.colour = colour;
        this.id = this.name().toLowerCase(Locale.ROOT);
    }

    /** Lower-case id used in lang keys ({@code trait.nerocompanion.<id>}, {@code chatter.nerocompanion.<id>.<n>}). */
    public String id() {
        return this.id;
    }

    /** Bubble border colour, {@code 0xAARRGGBB} (010 section 5.1). */
    public int colour() {
        return this.colour;
    }

    /** The trait a personality seed selects. Deterministic: the same seed always gives the same trait. */
    public static Trait fromSeed(long seed) {
        return VALUES[Math.floorMod(seed, COUNT)];
    }

    /** The trait for a synched index; out-of-range values (a newer server) fall back to {@link #CURIOUS}. */
    public static Trait byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : CURIOUS;
    }
}
