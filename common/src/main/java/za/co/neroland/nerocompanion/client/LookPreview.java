package za.co.neroland.nerocompanion.client;

/**
 * The Skin Studio's client-only preview: while the Studio draws its companion it sets the selected card's look
 * here, and {@link CompanionRenderInfo#fill} shows that look (awake, idle, healthy eyes) for that one entity during
 * that one draw. Nothing is sent. Client only.
 */
public final class LookPreview {

    private static int entityId = -1;
    private static String skin = "";
    private static String hat = "";

    private LookPreview() {
    }

    /** Begins a preview draw of {@code entityId} wearing {@code previewSkin} / {@code previewHat}. */
    public static void begin(int previewEntity, String previewSkin, String previewHat) {
        entityId = previewEntity;
        skin = previewSkin == null ? "" : previewSkin;
        hat = previewHat == null ? "" : previewHat;
    }

    public static void end() {
        entityId = -1;
    }

    static void apply(CompanionRenderInfo info) {
        if (entityId < 0 || info.entityId != entityId) {
            return;
        }
        info.preview = true;
        info.skin = skin;
        info.hat = hat;
        info.flags = 0;
        info.energyPct = 100;
        info.mode = za.co.neroland.nerocompanion.entity.CompanionMode.FOLLOW;
        info.aggressive = false;
        info.lungeTicks = 0;
    }
}
