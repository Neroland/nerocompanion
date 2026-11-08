package za.co.neroland.nerocompanion.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * Blits from the one widget sheet ({@code textures/gui/companion_widgets.png}, 256x256) at 1:1 GUI pixels, with
 * optional ARGB tint, a 3-slice bar trough and nine-slices. The only place the per-line blit signatures differ.
 * Client only.
 */
public final class Sheet {

    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "textures/gui/companion_widgets.png");
    public static final int SIZE = 256;
    public static final int WHITE = 0xFFFFFFFF;

    private Sheet() {
    }

    /** Draws the region {@code (u, v, w, h)} at {@code (x, y)}. */
    public static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        blit(g, x, y, w, h, u, v, w, h, WHITE);
    }

    /** Draws the region {@code (u, v, w, h)} tinted with {@code argb}. */
    public static void tinted(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h, int argb) {
        blit(g, x, y, w, h, u, v, w, h, argb);
    }

    /** Draws the region {@code (u, v, srcW, srcH)} stretched to {@code w x h}, tinted. */
    public static void blit(GuiGraphicsExtractor g, int x, int y, int w, int h, int u, int v, int srcW, int srcH,
            int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        //? if >=1.21.2 {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u, v, w, h, srcW, srcH, SIZE, SIZE, argb);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.setShaderColor(((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, ((argb >>> 24) & 0xFF) / 255.0F);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(TEXTURE, x, y, w, h, (float) u, (float) v, srcW, srcH, SIZE, SIZE);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        *///?}
    }

    /** A 3-slice horizontal trough: ends {@code cap} px, middle stretched. */
    public static void threeSlice(GuiGraphicsExtractor g, int x, int y, int w, int u, int v, int srcW, int h, int cap) {
        blit(g, x, y, cap, h, u, v, cap, h, WHITE);
        blit(g, x + cap, y, w - 2 * cap, h, u + cap, v, srcW - 2 * cap, h, WHITE);
        blit(g, x + w - cap, y, cap, h, u + srcW - cap, v, cap, h, WHITE);
    }

    /** A 16x16 nine-slice at {@code (u, v)} with {@code corner}-px corners, stretched to {@code w x h}. */
    public static void nine(GuiGraphicsExtractor g, int x, int y, int w, int h, int u, int v, int corner) {
        int c = corner;
        int m = 16 - 2 * c;
        int iw = w - 2 * c;
        int ih = h - 2 * c;
        blit(g, x, y, c, c, u, v, c, c, WHITE);
        blit(g, x + c, y, iw, c, u + c, v, m, c, WHITE);
        blit(g, x + w - c, y, c, c, u + 16 - c, v, c, c, WHITE);
        blit(g, x, y + c, c, ih, u, v + c, c, m, WHITE);
        blit(g, x + c, y + c, iw, ih, u + c, v + c, m, m, WHITE);
        blit(g, x + w - c, y + c, c, ih, u + 16 - c, v + c, c, m, WHITE);
        blit(g, x, y + h - c, c, c, u, v + 16 - c, c, c, WHITE);
        blit(g, x + c, y + h - c, iw, c, u + c, v + 16 - c, m, c, WHITE);
        blit(g, x + w - c, y + h - c, c, c, u + 16 - c, v + 16 - c, c, c, WHITE);
    }
}
