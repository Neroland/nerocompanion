package za.co.neroland.nerocompanion.client.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.personality.Emote;

/**
 * Line-neutral bubble geometry in billboard space (camera-facing; +x right, +y up, +z toward the camera; units are
 * blocks, 1 px = 0.025). The panel is flat colour on the shared white texture; icons come from
 * {@code textures/gui/emotes.png} once the art pass ships it - until then the layer draws the line's text
 * ({@link BubbleText}) instead. Client only.
 */
final class BubbleArt {

    static final Identifier EMOTES = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
            "textures/gui/emotes.png");
    static final int FULL_BRIGHT = 0x00F000F0;
    private static final float PX = 0.025F;
    private static final int FILL = 0x141C26;

    private static Boolean emotesPresent;

    private BubbleArt() {
    }

    /** Whether the emote sheet exists (checked once; it ships with the mod or not at all). */
    static boolean emotesPresent() {
        Boolean present = emotesPresent;
        if (present == null) {
            present = Minecraft.getInstance().getResourceManager().getResource(EMOTES).isPresent();
            emotesPresent = present;
        }
        return present;
    }

    /** Height of the tail under the panel, in blocks. */
    static final float TAIL = 3.0F * PX;

    /** Half-width (blocks) of an icon panel for one or two icons. */
    static float iconHalfWidth(boolean twoIcons) {
        return (twoIcons ? 28.0F : 20.0F) * PX / 2.0F;
    }

    /** Half-height (blocks) of an icon panel. */
    static float iconHalfHeight() {
        return 9.0F * PX;
    }

    /**
     * Border (trait colour), fill and tail of a panel {@code 2 hw x 2 hh} blocks centred on {@code (0, cy)}; the
     * tail hangs from the bottom centre.
     */
    static void panel(PoseStack.Pose pose, VertexConsumer c, float hw, float hh, float cy, int traitArgb,
            float alpha) {
        int border = argb(traitArgb, alpha);
        int fill = argb(0xFF000000 | FILL, alpha * 0.92F);
        quad(pose, c, -hw, cy - hh, hw, cy + hh, 0.0F, 0.5F, 0.5F, 0.5F, 0.5F, border);
        quad(pose, c, -hw + PX, cy - hh + PX, hw - PX, cy + hh - PX, 0.001F, 0.5F, 0.5F, 0.5F, 0.5F, fill);
        quad(pose, c, -1.5F * PX, cy - hh - TAIL, 1.5F * PX, cy - hh, 0.0F, 0.5F, 0.5F, 0.5F, 0.5F, border);
    }

    /** The one or two icons (16 px; a second icon sits 8 px to the right of the first), centred on {@code cy}. */
    static void icons(PoseStack.Pose pose, VertexConsumer c, Emote first, @Nullable Emote second, float cy,
            float alpha) {
        if (second == null) {
            icon(pose, c, first, 0.0F, cy, alpha);
        } else {
            icon(pose, c, first, -4.0F * PX, cy, alpha);
            icon(pose, c, second, 4.0F * PX, cy, alpha);
        }
    }

    private static void icon(PoseStack.Pose pose, VertexConsumer c, Emote emote, float cx, float cy, float alpha) {
        float h = 8.0F * PX;
        float u0 = emote.column() * 16.0F / 128.0F;
        float v0 = emote.row() * 16.0F / 64.0F;
        quad(pose, c, cx - h, cy - h, cx + h, cy + h, 0.002F, u0, v0, u0 + 0.125F, v0 + 0.25F, argb(0xFFFFFFFF, alpha));
    }

    /** {@code argb} with its alpha multiplied by {@code alpha}. */
    static int argb(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** A camera-facing quad; u0/v0 at the top-left. */
    private static void quad(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float x1, float y1, float z,
            float u0, float v0, float u1, float v1, int argb) {
        vertex(pose, c, x0, y0, z, u0, v1, argb);
        vertex(pose, c, x1, y0, z, u1, v1, argb);
        vertex(pose, c, x1, y1, z, u1, v0, argb);
        vertex(pose, c, x0, y1, z, u0, v0, argb);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, float u, float v,
            int argb) {
        c.addVertex(pose, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
