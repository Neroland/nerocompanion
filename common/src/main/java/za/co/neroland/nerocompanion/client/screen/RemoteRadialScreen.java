package za.co.neroland.nerocompanion.client.screen;


import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.ClientText;
import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.client.Screens;
import za.co.neroland.nerocompanion.client.gui.Sheet;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The Remote's job radial: eight wedges in the same positions for every companion, the "Come here" hub and the
 * Recall pill. Opens after holding the Remote for 4 ticks; releasing the use key on a wedge, the hub or the pill
 * confirms it, and releasing it with nothing selected leaves the radial open for a click. A left click confirms the
 * element under the cursor (a click on empty space closes), number keys 1-9 / 0 pick directly, the wheel or Tab
 * switches companion, Esc closes. The hit-testing lives in {@link RadialLayout}. It sends intents only; the result shows as the companion's emote and, on a deny, one action-bar sentence. Client only.
 */
public class RemoteRadialScreen extends Screen {

    /** Wedge tiles: x, w, h, cx, cy, gx, gy (rows y 112 normal / 144 hover / 208 active). */
    private static final int[][] WEDGES = {
            {0, 30, 27, 15, 40, 0, -27},
            {32, 31, 31, -6, 37, 19, -19},
            {64, 27, 30, -13, 15, 27, 0},
            {96, 31, 31, -6, -6, 19, 19},
            {128, 30, 27, 15, -13, 0, 27},
            {160, 31, 31, 37, -6, -19, 19},
            {192, 27, 30, 40, 15, -27, 0},
            {224, 31, 31, 37, 37, -19, -19}};

    /** {@link RadialLayout#NONE}, {@link RadialLayout#HUB}, 0..7 wedge or {@link RadialLayout#RECALL}. */
    private int hovered = RadialLayout.NONE;
    private int flash;
    /**
     * Whether the cursor has left the centre since the radial opened. The cursor starts on the hub, so until it
     * moves at least {@link RadialLayout#MOVE_PX} px a release selects nothing (it must not send "Come here").
     */
    private boolean moved;

    public RemoteRadialScreen() {
        super(Component.translatable("gui.nerocompanion.radial.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private static Payloads.HudEntry target() {
        return ClientState.highlightedEntry();
    }

    @Override
    public void tick() {
        if (this.flash > 0) {
            this.flash--;
        }
        if (target() == null) {
            Screens.set(null);
        }
    }

    @Nullable
    private Component denial(Payloads.HudEntry t, CompanionMode mode) {
        if (!t.supports(mode)) {
            java.util.Optional<za.co.neroland.nerocompanion.chip.ChipKind> chip =
                    za.co.neroland.nerocompanion.chip.ChipEffects.unlockerOf(mode);
            return t.needsChip(mode) && chip.isPresent() ? Component.translatable("gui.nerocompanion.reason.chip",
                    Component.translatable("item.nerocompanion." + chip.get().itemName(1)))
                    : Component.translatable("gui.nerocompanion.reason.type", ClientText.typeName(t.type()));
        }
        if (t.has(Payloads.HudEntry.LOW_BATTERY) && mode.isActiveDrain()) {
            return Component.translatable("gui.nerocompanion.reason.low_battery");
        }
        return null;
    }

    private int pick(double mx, double my) {
        return this.pick(mx, my, false);
    }

    /** The element under the cursor; {@code click} lets a deliberate left click choose the hub without moving. */
    private int pick(double mx, double my, boolean click) {
        double dx = mx - this.width / 2;
        double dy = my - this.height / 2;
        if (RadialLayout.movedOff(dx, dy)) {
            this.moved = true;
        }
        return RadialLayout.pick(dx, dy, this.moved, click, this.hovered);
    }

    private void draw(GuiGraphicsExtractor g, int mx, int my) {
        Payloads.HudEntry t = target();
        if (t == null) {
            return;
        }
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.hovered = this.pick(mx, my);
        // name plate
        Sheet.nine(g, cx - 60, cy - 66, 120, 14, 0, 240, 4);
        Sheet.blit(g, cx - 56, cy - 63, 32 + 8 * ClientText.typeIndex(t.type()), 56, 8, 8);
        Component plate = Component.translatable("gui.nerocompanion.radial.target",
                ClientText.displayName(t.name(), t.type()), ClientText.typeName(t.type()));
        String plateText = plate.getString();
        boolean several = ClientState.hud().stream().filter(e -> e.has(Payloads.HudEntry.LOADED)).count() > 1;
        int room = several ? 60 : 104;
        g.text(this.font, this.fit(plateText, room), cx - 46, cy - 63, CompanionColours.TITLE, false);
        if (several) {
            g.text(this.font, this.fit(Component.translatable("gui.nerocompanion.radial.cycle_hint").getString(), 50),
                    cx + 8, cy - 63, CompanionColours.SUBTLE, false);
        }
        // wedges
        CompanionMode[] modes = CompanionMode.values();
        for (int i = 0; i < 8; i++) {
            int[] w = WEDGES[i];
            CompanionMode mode = modes[i];
            boolean disabled = this.denial(t, mode) != null;
            int row = mode == t.mode() ? 208 : this.hovered == i ? 144 : 112;
            Sheet.blit(g, cx - w[3], cy - w[4], w[1], w[2], w[0], row, w[1], w[2],
                    disabled && row == 112 ? 0x80FFFFFF : Sheet.WHITE);
            Sheet.blit(g, cx + w[5] - 8, cy + w[6] - 8, 16 * i, disabled ? 16 : 0, 16, 16);
            double rad = Math.toRadians(i * 45.0D);
            int lx = cx + (int) Math.round(Math.sin(rad) * 46.0D) - 2;
            int ly = cy - (int) Math.round(Math.cos(rad) * 46.0D) - 4;
            g.text(this.font, String.valueOf(i + 1), lx, ly, CompanionColours.SUBTLE, false);
        }
        // hub + needle
        Sheet.blit(g, cx - 14, cy - 14, this.hovered == -1 ? 112 : 224, this.hovered == -1 ? 80 : 176, 28, 28);
        Sheet.blit(g, cx - 8, cy - 8, 112, 32, 16, 16);
        if (this.hovered >= 0 && this.hovered < 8) {
            double rad = Math.toRadians(this.hovered * 45.0D);
            Sheet.blit(g, cx + (int) Math.round(Math.sin(rad) * 10.0D) - 4,
                    cy - (int) Math.round(Math.cos(rad) * 10.0D) - 4, 176, 80, 8, 8);
        }
        // the line under the ring
        Component line = null;
        int colour = CompanionColours.TITLE;
        if (this.hovered >= 0 && this.hovered < 8) {
            CompanionMode mode = modes[this.hovered];
            Component denial = this.denial(t, mode);
            if (denial != null) {
                line = denial;
                colour = this.flash > 0 && this.flash % 4 < 2 ? CompanionColours.TITLE : CompanionColours.DENY_TEXT;
            } else {
                line = Component.translatable("gui.nerocompanion.radial.line", ClientText.modeName(mode),
                        Component.translatable("mode.nerocompanion." + mode.id() + ".tip"));
            }
        } else if (this.hovered == -1) {
            line = Component.translatable("gui.nerocompanion.radial.come_here");
        } else if (this.hovered == 8) {
            line = Component.translatable("gui.nerocompanion.companion.recall.tip");
        }
        if (line != null) {
            String text = this.fit(line.getString(), 176);
            g.text(this.font, text, cx - this.font.width(text) / 2, cy + 46, colour, false);
        }
        // recall pill
        boolean pillHot = this.hovered == 8;
        int pillX = cx - 48;
        int pillY = cy + 58;
        g.fill(pillX, pillY, pillX + 96, pillY + 14, pillHot ? CompanionColours.EDGE_HOT : CompanionColours.PRIMARY_EDGE);
        g.fill(pillX + 1, pillY + 1, pillX + 95, pillY + 13,
                pillHot ? CompanionColours.BUTTON_HOVER : CompanionColours.BUTTON_BODY);
        String pill = Component.translatable("gui.nerocompanion.radial.recall").getString();
        g.text(this.font, pill, cx - this.font.width(pill) / 2, pillY + 3, CompanionColours.TITLE, false);
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…"))) + "…";
    }

    /** Sends the hovered element (or {@code index}); closes after a send, flashes on a disabled wedge. */
    private void confirm(int index) {
        Payloads.HudEntry t = target();
        if (t == null) {
            Screens.set(null);
            return;
        }
        if (index == RadialLayout.NONE) {
            Screens.set(null);
            return;
        }
        CustomPacketPayload payload;
        if (index == -1) {
            payload = new Payloads.SetMode(t.companionId(), CompanionMode.FOLLOW, true);
        } else if (index == 8) {
            payload = new Payloads.Recall(t.companionId());
        } else {
            CompanionMode mode = CompanionMode.values()[index];
            if (this.denial(t, mode) != null) {
                this.flash = 8;
                return;
            }
            if (mode == t.mode()) {
                // Already in that mode: nothing to send, but the choice was made, so close like any confirm.
                CompanionScreenBase.clickSound();
                Screens.set(null);
                return;
            }
            payload = new Payloads.SetMode(t.companionId(), mode, false);
        }
        Services.NETWORK.sendToServer(payload);
        CompanionScreenBase.clickSound();
        Screens.set(null);
    }

    private void click(int picked) {
        this.hovered = picked;
        if (RadialLayout.onClick(picked) == RadialLayout.Action.CLOSE) {
            Screens.set(null);
        } else {
            this.confirm(picked);
        }
    }

    private void useReleased(int picked) {
        if (RadialLayout.onUseRelease(picked) == RadialLayout.Action.CONFIRM) {
            this.confirm(picked);
        }
    }

    private boolean key(int key) {
        if (key == InputConstants.KEY_ESCAPE) {
            Screens.set(null);
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            ClientState.cycle(1);
            return true;
        }
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_8) {
            this.confirm(key - InputConstants.KEY_1);
            return true;
        }
        if (key == InputConstants.KEY_9) {
            this.confirm(-1);
            return true;
        }
        if (key == InputConstants.KEY_0) {
            this.confirm(8);
            return true;
        }
        return false;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.draw(g, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }
    //?} else {
    /*@Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.draw(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.click(this.pick(event.x(), event.y(), true));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.useReleased(this.pick(event.x(), event.y()));
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.key(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.click(this.pick(mx, my, true));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.useReleased(this.pick(mx, my));
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return this.key(key) || super.keyPressed(key, scan, modifiers);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        ClientState.cycle(scrollY > 0 ? -1 : 1);
        return true;
    }

    /** Opens the radial addressed at {@code companion} (the crosshair target), else the highlighted one. */
    public static void open(@Nullable java.util.UUID companion) {
        if (companion != null) {
            ClientState.highlight(companion);
        }
        if (ClientState.highlightedEntry() == null) {
            Screens.actionBar(Component.translatable("gui.nerocompanion.remote.none"));
            return;
        }
        Screens.set(new RemoteRadialScreen());
    }

}
