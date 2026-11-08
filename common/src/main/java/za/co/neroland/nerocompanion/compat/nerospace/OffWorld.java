package za.co.neroland.nerocompanion.compat.nerospace;

/**
 * Where a companion is, as far as off-world rules care: whether the level is off-Earth (Core {@code SpaceTags}),
 * the flat gravity factor there ({@code 1.0} = Earth) and whether its position is irradiated. World facts only:
 * nothing about a player.
 */
public record OffWorld(boolean offEarth, double gravity, boolean radiation) {

    /** Earth, or any level Core's space tags do not list (every level on a Core-only server). */
    public static final OffWorld EARTH = new OffWorld(false, 1.0D, false);
    /** Off-Earth by the tags, but no planet data (Nerospace absent): Earth rules apply. */
    public static final OffWorld SPACE_UNKNOWN = new OffWorld(true, 1.0D, false);
}
