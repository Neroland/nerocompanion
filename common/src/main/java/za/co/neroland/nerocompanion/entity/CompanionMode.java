package za.co.neroland.nerocompanion.entity;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * The eight companion modes. One enum, persisted by its lower-case id. Which modes a companion may use is
 * declared per type (and later extended by capability chips); {@link #SLEEP} is the low-power idle the server
 * also forces when the owner is offline, too far away, or the battery is flat.
 */
public enum CompanionMode {
    FOLLOW,
    STAY,
    GUARD,
    MINE,
    CARRY,
    REPAIR,
    SCOUT,
    SLEEP;

    /** Lower-case id used in JSON, NBT and commands ({@code follow}, {@code stay}, ...). */
    public static final Codec<CompanionMode> CODEC = Codec.STRING.comapFlatMap(
            name -> byId(name).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown companion mode: " + name)),
            CompanionMode::id);

    private static final CompanionMode[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode for an id, or empty for an unknown one (a validation issue, never a crash). */
    public static Optional<CompanionMode> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (CompanionMode mode : VALUES) {
            if (mode.id().equals(id.toLowerCase(Locale.ROOT))) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /** The mode for a synched ordinal; out-of-range values read as {@link #SLEEP}. */
    public static CompanionMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SLEEP;
    }

    /**
     * Whether this mode counts as "active" for energy drain: GUARD, MINE, CARRY, REPAIR and SCOUT.
     * FOLLOW and STAY are idle; SLEEP drains nothing.
     */
    public boolean isActiveDrain() {
        return this == GUARD || this == MINE || this == CARRY || this == REPAIR || this == SCOUT;
    }
}
