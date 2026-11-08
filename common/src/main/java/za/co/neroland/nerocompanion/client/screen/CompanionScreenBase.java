package za.co.neroland.nerocompanion.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.client.gui.Sheet;
import za.co.neroland.nerocompanion.network.Feedbacks;
import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * The shared hull of every NeroCompanion screen (the family's procedural panel): panel, edge, ink line, title at
 * y+6, subtitle at y+17, a 14-px footer trough carrying the last result (green ok, red deny, grey hint), and an
 * 11x11 close button. Subclasses draw with {@link #drawBody}, react through {@link #click}/{@link #key}/
 * {@link #scroll}, and queue hover text with {@link #tooltip}. Every per-line difference of the container-screen
 * API (render entry points, input events, tooltips) is absorbed here. Client only.
 */
public abstract class CompanionScreenBase<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected static final int FOOTER = 14;
    /** Intent widgets wait this long for an answer before saying there was none. */
    protected static final int WAIT_TICKS = 100;

    @Nullable
    private List<Component> pendingTooltip;
    @Nullable
    private Component footer;
    private int footerColour = CompanionColours.SUBTLE;
    private int waiting = -1;

    protected CompanionScreenBase(T menu, Inventory inventory, Component title, int width, int height) {
        //? if >=26.1 {
        super(menu, inventory, title, width, height);
        //?} else {
        /*super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
        *///?}
    }

    @Override
    protected void init() {
        super.init();
        // Short windows: keep the title and body on screen; only the footer may clip.
        this.topPos = Math.max(2, (this.height - this.imageHeight) / 2);
    }

    // =================================================================================================
    // Subclass hooks
    // =================================================================================================

    /** The subtitle under the title (what this screen controls). */
    protected abstract Component subtitle();

    /** Draws everything inside the hull. {@code mx, my} are absolute GUI coordinates. */
    protected abstract void drawBody(GuiGraphicsExtractor g, int mx, int my, float partialTick);

    /** A click inside the screen; true when handled. */
    protected boolean click(double mx, double my, int button) {
        return false;
    }

    /** A key; true when handled (before vanilla handling). */
    protected boolean key(int key) {
        return false;
    }

    protected boolean scroll(double mx, double my, double amount) {
        return false;
    }

    protected boolean drag(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    /** The text box that currently takes typing, or null. */
    @Nullable
    protected EditBox typingBox() {
        return null;
    }

    /** Called when the user presses Escape; default closes. */
    protected void escape() {
        this.onClose();
    }

    // =================================================================================================
    // Shared state: footer, waiting, tooltips
    // =================================================================================================

    protected void showFeedback(Payloads.Feedback feedback) {
        if (feedback.key().isEmpty()) {
            return;
        }
        this.footer = Feedbacks.component(feedback);
        this.footerColour = feedback.ok() ? CompanionColours.OK_TEXT : CompanionColours.DENY_TEXT;
    }

    protected void hint(Component text) {
        this.footer = text;
        this.footerColour = CompanionColours.SUBTLE;
    }

    protected void deny(Component text) {
        this.footer = text;
        this.footerColour = CompanionColours.DENY_TEXT;
    }

    protected void startWaiting() {
        this.waiting = 0;
    }

    protected void stopWaiting() {
        this.waiting = -1;
    }

    protected boolean isWaiting() {
        return this.waiting >= 0;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.waiting >= 0 && ++this.waiting > WAIT_TICKS) {
            this.waiting = -1;
            this.deny(Component.translatable("gui.nerocompanion.no_answer"));
        }
    }

    protected void tooltip(Component... lines) {
        this.pendingTooltip = List.of(lines);
    }

    protected void tooltip(List<Component> lines) {
        this.pendingTooltip = List.copyOf(lines);
    }

    protected static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(za.co.neroland.nerocompanion.registry.ModSounds.UI_CLICK.get(), 1.0F));
    }

    protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // =================================================================================================
    // Drawing helpers
    // =================================================================================================

    protected void text(GuiGraphicsExtractor g, Component text, int x, int y, int colour) {
        g.text(this.font, text, x, y, colour, false);
    }

    protected void text(GuiGraphicsExtractor g, String text, int x, int y, int colour) {
        g.text(this.font, text, x, y, colour, false);
    }

    /** {@code text} cut to {@code width} px with an ellipsis. */
    protected String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        String cut = this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…")));
        return cut + "…";
    }

    /** {@code text} word-wrapped into lines of the given widths; ends in an ellipsis when it does not all fit. */
    protected List<String> wrap(String text, int... widths) {
        return za.co.neroland.nerocompanion.client.gui.TextWrap.wrap(text, this.font::width, widths);
    }

    /** {@code text} on one line of {@code width} px, cut between words with an ellipsis. */
    protected String fitWords(String text, int width) {
        return za.co.neroland.nerocompanion.client.gui.TextWrap.fitWords(text, this.font::width, width);
    }

    /** A procedural button: body + 1-px edge, centred label, the three states plus primary / selected. */
    protected void button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, boolean enabled,
            boolean hovered, boolean primary, boolean selected) {
        int body = !enabled ? CompanionColours.DISABLED_BODY : selected ? CompanionColours.SELECTED_BODY
                : hovered ? CompanionColours.BUTTON_HOVER : CompanionColours.BUTTON_BODY;
        int edge = !enabled ? CompanionColours.DISABLED_EDGE : hovered ? CompanionColours.EDGE_HOT
                : primary || selected ? CompanionColours.PRIMARY_EDGE : CompanionColours.BUTTON_EDGE;
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        int colour = !enabled ? CompanionColours.DISABLED_TEXT : CompanionColours.TITLE;
        String fitted = this.fit(label.getString(), w - 4);
        this.text(g, fitted, x + (w - this.font.width(fitted)) / 2, y + (h - 8) / 2, colour);
    }

    private void drawHull(GuiGraphicsExtractor g, int mx, int my) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        g.fill(x, y, x + w, y + h, CompanionColours.EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CompanionColours.INK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, CompanionColours.PANEL);
        g.fill(x + 2, y + h - FOOTER, x + w - 2, y + h - 2, CompanionColours.TROUGH);
        this.text(g, this.fit(this.getTitle().getString(), w - 24), x + 8, y + 6, CompanionColours.TITLE);
        this.text(g, this.fit(this.subtitle().getString(), w - 16), x + 8, y + 17, CompanionColours.SUBTLE);
        int cx = x + w - 11;
        int cy = y + 5;
        boolean hot = inside(mx, my, cx, cy, 11, 11);
        g.fill(cx, cy, cx + 11, cy + 11, hot ? CompanionColours.EDGE_HOT : CompanionColours.BUTTON_EDGE);
        g.fill(cx + 1, cy + 1, cx + 10, cy + 10, hot ? CompanionColours.BUTTON_HOVER : CompanionColours.BUTTON_BODY);
        Sheet.blit(g, cx + 1, cy + 1, 200, 240, 8, 8);
        if (hot) {
            this.tooltip(Component.translatable("gui.back"));
        }
        Component line = this.isWaiting() ? Component.translatable("gui.nerocompanion.waiting") : this.footer;
        if (line != null) {
            this.text(g, this.fit(line.getString(), w - 16), x + 8, y + h - FOOTER + 3,
                    this.isWaiting() ? CompanionColours.SUBTLE : this.footerColour);
        }
    }

    private void drawAll(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        this.pendingTooltip = null;
        this.drawHull(g, mx, my);
        this.drawBody(g, mx, my, partialTick);
    }

    private void flushTooltip(GuiGraphicsExtractor g, int mx, int my) {
        List<Component> lines = this.pendingTooltip;
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(this.font.split(line, 200));
        }
        //? if >=1.21.6 {
        g.setTooltipForNextFrame(wrapped, mx, my);
        //?} else {
        /*g.renderTooltip(this.font, wrapped, mx, my);
        *///?}
    }

    // =================================================================================================
    // Per-line entry points
    // =================================================================================================

    //? if >=26.1 {
    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.drawAll(g, mouseX, mouseY, partialTick);
        super.extractContents(g, mouseX, mouseY, partialTick);
        this.flushTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }
    //?} else {
    /*@Override
    protected void renderBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        this.drawAll(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.flushTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }
    *///?}

    private boolean handleClick(double mx, double my, int button) {
        int cx = this.leftPos + this.imageWidth - 11;
        int cy = this.topPos + 5;
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(mx, my, cx, cy, 11, 11)) {
            clickSound();
            this.escape();
            return true;
        }
        return this.click(mx, my, button);
    }

    private boolean handleKey(int key) {
        if (key == InputConstants.KEY_ESCAPE) {
            this.escape();
            return true;
        }
        return this.key(key);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.handleClick(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.drag(event.x(), event.y(), event.button(), dx, dy)) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.handleKey(event.key())) {
            return true;
        }
        EditBox box = this.typingBox();
        if (box != null && box.isFocused()) {
            box.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.handleClick(mx, my, button)) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.drag(mx, my, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (this.handleKey(key)) {
            return true;
        }
        EditBox box = this.typingBox();
        if (box != null && box.isFocused()) {
            box.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.scroll(mx, my, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}
