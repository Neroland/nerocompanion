package za.co.neroland.nerocompanion.entity;

import org.jetbrains.annotations.Nullable;

/**
 * What a creative-gallery display companion shows ({@code /nerocompanion gallery}). Display only and never saved: the
 * companion is frozen (no AI, no drain, no work) and the synched mode, flags, energy and bond level come from here
 * instead of its record. {@code -1} (or a null mode) keeps the real value.
 *
 * @param mode          the mode shown to clients, or null for the companion's own (STAY in the gallery)
 * @param flags         the {@code CompanionEntity.FLAG_*} pose bits shown (perched is never set: it needs a player)
 * @param energyPercent the energy shown, 0..100, or -1 for the real charge
 * @param bondLevel     the bond level shown, 0..4, or -1 for the real one
 * @param nameVisible   whether the nameplate shows without looking at the companion
 */
public record GalleryPose(@Nullable CompanionMode mode, byte flags, int energyPercent, int bondLevel,
        boolean nameVisible) {

    /** Frozen in place, otherwise as it is. */
    public static GalleryPose still() {
        return new GalleryPose(null, (byte) 0, -1, -1, false);
    }

    /** Powered down (the sleep pose). */
    public static GalleryPose asleep() {
        return new GalleryPose(CompanionMode.SLEEP, CompanionEntity.FLAG_SLEEPING, -1, -1, false);
    }

    /** The nap pose (the Scrapling's curl-up). */
    public static GalleryPose napping() {
        return new GalleryPose(null, CompanionEntity.FLAG_NAPPING, -1, -1, false);
    }

    /** Running its work loop in {@code mode}. */
    public static GalleryPose working(CompanionMode mode) {
        return new GalleryPose(mode, CompanionEntity.FLAG_WORKING, -1, -1, false);
    }

    /** Docked and charging (the client reads working + asleep as charging). */
    public static GalleryPose charging() {
        return new GalleryPose(CompanionMode.SLEEP,
                (byte) (CompanionEntity.FLAG_WORKING | CompanionEntity.FLAG_SLEEPING), 60, -1, false);
    }

    /** Awake at {@code percent} charge (the eye colour follows the band). */
    public static GalleryPose energy(int percent) {
        return new GalleryPose(null, (byte) 0, Math.max(0, Math.min(100, percent)), -1, false);
    }

    /** At bond {@code level}, nameplate showing. */
    public static GalleryPose bond(int level) {
        return new GalleryPose(null, (byte) 0, -1, Math.max(0, Math.min(4, level)), true);
    }
}
