package za.co.neroland.nerocompanion.client.hud;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.ClientText;
import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.client.gui.Sheet;
import za.co.neroland.nerocompanion.client.gui.UiFormat;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * The HUD companion strip: one entry per LIVE companion of yours (type glyph, charge bar, job glyph, bond heart,
 * name), from {@code HudSync}. Collapses to compact (no name) under 400x240 GUI px and to minimal (glyph + bar,
 * stacked sideways) under 320 wide. Hidden with F1, when empty, and by {@code hud.enabled = false} or the toggle
 * key. Nothing here is hoverable. Client only.
 */
public final class CompanionHud {

    private static final int FULL_W = 110;
    private static final int COMPACT_W = 60;
    private static final int ENTRY_H = 12;
    private static final int MINIMAL_W = 12;
    private static final int MINIMAL_H = 14;
    private static final int GAP = 2;
    private static final int MARGIN = 4;

    private CompanionHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        NeroCompanionConfig.Hud config = NeroCompanionConfig.hud();
        List<Payloads.HudEntry> entries = ClientState.hud();
        if (za.co.neroland.nerocompanion.client.Screens.guiHidden() || !config.enabled() || ClientState.hudHidden() || entries.isEmpty()) {
            return;
        }
        int gw = g.guiWidth();
        int gh = g.guiHeight();
        boolean minimal = gw < 320;
        boolean compact = !minimal && (gw < 400 || gh < 240 || config.compact() || !config.showNames());
        int max = Math.min(NeroCompanionConfig.limits().maxCompanionsPerPlayer(), 6);
        int shown = Math.min(entries.size(), max);
        boolean more = entries.size() > max;
        int ew = minimal ? MINIMAL_W : compact ? COMPACT_W : FULL_W;
        int eh = minimal ? MINIMAL_H : ENTRY_H;
        int rows = shown + (more ? 1 : 0);
        int totalW = minimal ? rows * (ew + GAP) - GAP : ew;
        int totalH = minimal ? eh : rows * (eh + GAP) - GAP;
        String anchor = config.anchor().toUpperCase(Locale.ROOT);
        int x = anchor.endsWith("LEFT") ? MARGIN : anchor.endsWith("RIGHT") ? gw - MARGIN - totalW : (gw - totalW) / 2;
        int y = anchor.startsWith("TOP") ? MARGIN : anchor.startsWith("BOTTOM") ? gh - MARGIN - totalH
                : (gh - totalH) / 2;
        x = Math.max(0, Math.min(gw - totalW, x + config.offsetX()));
        y = Math.max(0, Math.min(gh - totalH, y + config.offsetY()));
        boolean upward = anchor.startsWith("BOTTOM");
        long time = mc.level == null ? 0L : mc.level.getGameTime();
        int highlighted = ClientState.highlightedIndex();
        for (int i = 0; i < shown; i++) {
            Payloads.HudEntry e = entries.get(i);
            int ex = minimal ? x + i * (ew + GAP) : x;
            int ey = minimal ? y : upward ? y + totalH - eh - i * (eh + GAP) : y + i * (eh + GAP);
            if (minimal) {
                minimal(g, e, ex, ey);
            } else {
                entry(g, mc.font, e, ex, ey, !compact, i == highlighted, time);
            }
        }
        if (more) {
            int ey = minimal ? y : upward ? y + totalH - eh - shown * (eh + GAP) : y + shown * (eh + GAP);
            int ex = minimal ? x + shown * (ew + GAP) : x;
            g.text(mc.font, Component.translatable("gui.nerocompanion.hud.more", entries.size() - max), ex + 4, ey + 2,
                    CompanionColours.SUBTLE, true);
        }
    }

    private static int fillU(int pct, boolean sleeping) {
        if (sleeping) {
            return -1;
        }
        return pct >= 100 ? 192 : pct <= 25 ? 128 : pct <= 50 ? 64 : 0;
    }

    private static void entry(GuiGraphicsExtractor g, Font font, Payloads.HudEntry e, int x, int y, boolean names,
            boolean highlighted, long time) {
        boolean loaded = e.has(Payloads.HudEntry.LOADED);
        boolean sleeping = e.has(Payloads.HudEntry.SLEEPING);
        g.fill(x, y, x + (highlighted ? 3 : 2), y + ENTRY_H,
                highlighted ? CompanionColours.TEAL_BRIGHT : CompanionColours.COMPANION_TEAL);
        Sheet.tinted(g, x + 4, y + 2, 32 + 8 * ClientText.typeIndex(e.type()), 56, 8, 8,
                loaded ? Sheet.WHITE : 0x80FFFFFF);
        Sheet.threeSlice(g, x + 14, y + 3, 24, loaded ? 0 : 128, 64, 64, 6, 2);
        if (loaded) {
            int fill = UiFormat.fill(e.energyPct(), 22);
            int u = fillU(e.energyPct(), sleeping);
            Sheet.blit(g, x + 15, y + 4, fill, 4, u < 0 ? 64 : u, u < 0 ? 64 : 72, fill, 4, Sheet.WHITE);
        }
        boolean blink = e.has(Payloads.HudEntry.LOW_BATTERY) && (time / 10L) % 2L == 0L;
        if (blink) {
            Sheet.blit(g, x + 40, y + 2, 224, 240, 8, 8);
        } else {
            Sheet.blit(g, x + 40, y + 2, 32 + 8 * e.mode().ordinal(), 48, 8, 8);
        }
        if (e.bondLevel() >= 3) {
            Sheet.blit(g, x + 50, y + 3, 24, 48, 5, 5);
        }
        if (names) {
            String name = ClientText.displayName(e.name(), e.type()).getString();
            if (font.width(name) > 50) {
                name = font.plainSubstrByWidth(name, 50 - font.width("…")) + "…";
            }
            g.fill(x + 58, y + 11, x + 58 + font.width(name), y + 12, 0x80000000);
            g.text(font, name, x + 58, y + 2, sleeping ? CompanionColours.SUBTLE : CompanionColours.TITLE, true);
        }
    }

    private static void minimal(GuiGraphicsExtractor g, Payloads.HudEntry e, int x, int y) {
        boolean loaded = e.has(Payloads.HudEntry.LOADED);
        Sheet.tinted(g, x + 2, y, 32 + 8 * ClientText.typeIndex(e.type()), 56, 8, 8, loaded ? Sheet.WHITE : 0x80FFFFFF);
        int pct = e.energyPct();
        int colour = e.has(Payloads.HudEntry.SLEEPING) ? CompanionColours.ENERGY_SLEEP : pct >= 100
                ? CompanionColours.ENERGY_FULL : pct <= 25 ? CompanionColours.ENERGY_LOW : pct <= 50
                        ? CompanionColours.ENERGY_MID : CompanionColours.ENERGY_HI;
        g.fill(x, y + 10, x + UiFormat.fill(pct, 12), y + 12, loaded ? colour : CompanionColours.DISABLED_EDGE);
    }
}
