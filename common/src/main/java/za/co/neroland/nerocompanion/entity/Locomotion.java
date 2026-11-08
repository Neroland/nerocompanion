package za.co.neroland.nerocompanion.entity;

import java.util.Locale;
import java.util.Optional;

/** How a companion chassis moves; picks the concrete entity class ({@code FlyingCompanion} / {@code GroundCompanion}). */
public enum Locomotion {
    FLYING,
    GROUND;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Locomotion> byId(String id) {
        for (Locomotion locomotion : values()) {
            if (locomotion.id().equals(id)) {
                return Optional.of(locomotion);
            }
        }
        return Optional.empty();
    }
}
