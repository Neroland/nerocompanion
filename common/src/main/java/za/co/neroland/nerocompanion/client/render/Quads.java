package za.co.neroland.nerocompanion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * Flat-colour quads for the chargers' block-entity renderers, in block-local pixels (0..16), rotated about the block
 * centre by the block's facing so no pose rotation call is needed (its name differs between lines). Client only.
 */
public final class Quads {

    /** A plain white texture; colour comes from the vertex. */
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
            "textures/misc/white.png");
    public static final int FULL_BRIGHT = 0x00F000F0;

    private Quads() {
    }

    /** Quarter turns that map a north-facing layout onto {@code facing}. */
    public static int quarterTurns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private static float[] rotate(float x, float z, int turns) {
        float px = x;
        float pz = z;
        for (int i = 0; i < turns; i++) {
            float nx = 16.0F - pz;
            pz = px;
            px = nx;
        }
        return new float[] {px / 16.0F, pz / 16.0F};
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, int argb, int light,
            float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** A horizontal quad at height {@code y} (pixels) covering {@code x0..x1, z0..z1}, facing up. */
    public static void floor(VertexConsumer c, PoseStack.Pose pose, int turns, float x0, float z0, float x1, float z1,
            float y, int argb, int light) {
        float[] a = rotate(x0, z0, turns);
        float[] b = rotate(x0, z1, turns);
        float[] d = rotate(x1, z1, turns);
        float[] e = rotate(x1, z0, turns);
        float h = y / 16.0F;
        vertex(c, pose, a[0], h, a[1], argb, light, 0, 1, 0);
        vertex(c, pose, b[0], h, b[1], argb, light, 0, 1, 0);
        vertex(c, pose, d[0], h, d[1], argb, light, 0, 1, 0);
        vertex(c, pose, e[0], h, e[1], argb, light, 0, 1, 0);
    }

    /**
     * A vertical quad on the front plane {@code z} (pixels, north-facing layout) covering {@code x0..x1, y0..y1},
     * drawn on both sides.
     */
    public static void wall(VertexConsumer c, PoseStack.Pose pose, int turns, float z, float x0, float x1, float y0,
            float y1, int argb, int light) {
        float[] a = rotate(x0, z, turns);
        float[] b = rotate(x1, z, turns);
        float ya = y0 / 16.0F;
        float yb = y1 / 16.0F;
        vertex(c, pose, a[0], ya, a[1], argb, light, 0, 0, -1);
        vertex(c, pose, a[0], yb, a[1], argb, light, 0, 0, -1);
        vertex(c, pose, b[0], yb, b[1], argb, light, 0, 0, -1);
        vertex(c, pose, b[0], ya, b[1], argb, light, 0, 0, -1);
        vertex(c, pose, b[0], ya, b[1], argb, light, 0, 0, 1);
        vertex(c, pose, b[0], yb, b[1], argb, light, 0, 0, 1);
        vertex(c, pose, a[0], yb, a[1], argb, light, 0, 0, 1);
        vertex(c, pose, a[0], ya, a[1], argb, light, 0, 0, 1);
    }

    /** A small box (pixels) in the north-facing layout: front, top and two sides. */
    public static void box(VertexConsumer c, PoseStack.Pose pose, int turns, float x0, float y0, float z0, float x1,
            float y1, float z1, int argb, int light) {
        wall(c, pose, turns, z0, x0, x1, y0, y1, argb, light);
        floor(c, pose, turns, x0, z0, x1, z1, y1, darker(argb), light);
        sideX(c, pose, turns, x0, y0, y1, z0, z1, darker(argb), light);
        sideX(c, pose, turns, x1, y0, y1, z0, z1, darker(argb), light);
    }

    private static void sideX(VertexConsumer c, PoseStack.Pose pose, int turns, float x, float y0, float y1, float z0,
            float z1, int argb, int light) {
        float[] a = rotate(x, z0, turns);
        float[] b = rotate(x, z1, turns);
        float ya = y0 / 16.0F;
        float yb = y1 / 16.0F;
        vertex(c, pose, a[0], ya, a[1], argb, light, 1, 0, 0);
        vertex(c, pose, a[0], yb, a[1], argb, light, 1, 0, 0);
        vertex(c, pose, b[0], yb, b[1], argb, light, 1, 0, 0);
        vertex(c, pose, b[0], ya, b[1], argb, light, 1, 0, 0);
        vertex(c, pose, b[0], ya, b[1], argb, light, -1, 0, 0);
        vertex(c, pose, b[0], yb, b[1], argb, light, -1, 0, 0);
        vertex(c, pose, a[0], yb, a[1], argb, light, -1, 0, 0);
        vertex(c, pose, a[0], ya, a[1], argb, light, -1, 0, 0);
    }

    private static int darker(int argb) {
        int r = (int) (((argb >> 16) & 0xFF) * 0.75F);
        int g = (int) (((argb >> 8) & 0xFF) * 0.75F);
        int b = (int) ((argb & 0xFF) * 0.75F);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
