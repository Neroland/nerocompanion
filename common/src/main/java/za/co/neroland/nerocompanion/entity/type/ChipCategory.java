package za.co.neroland.nerocompanion.entity.type;

import java.util.Locale;
import java.util.Optional;

/** Chip categories a chassis can accept (chips themselves arrive in Stage 2). */
public enum ChipCategory {
    STAT,
    CAPABILITY,
    COSMETIC;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ChipCategory> byId(String id) {
        for (ChipCategory category : values()) {
            if (category.id().equals(id)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
