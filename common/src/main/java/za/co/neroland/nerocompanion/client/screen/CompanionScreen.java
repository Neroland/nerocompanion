package za.co.neroland.nerocompanion.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.chip.ChipItem;
import za.co.neroland.nerocompanion.chip.ChipKind;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.ClientText;
import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.client.gui.Sheet;
import za.co.neroland.nerocompanion.client.gui.UiFormat;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;
import za.co.neroland.nerocompanion.menu.CompanionMenu;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.registry.ModItems;

/**
 * The Companion screen (248x150): name, job, energy, bond, chips, look and recall for one companion you own.
 * Draws only from the owner-only {@code CompanionSnapshot}; every control sends an intent the server re-validates.
 * Chips move by holding the chip and clicking a slot (no inventory on this screen). Client only.
 */
public class CompanionScreen extends CompanionScreenBase<CompanionMenu> {

    public static final int WIDTH = 248;
    public static final int HEIGHT = 150;
    private static final int[] MODE_X = {132, 186};
    private static final int[] MODE_Y = {40, 58, 76, 94};
    private static final int CHIP_Y = 112;

    /** Focus order: name, save, 8 modes, chips (0-2), Skin Studio, recall. */
    private static final int F_NAME = 0;
    private static final int F_SAVE = 1;
    private static final int F_MODE = 2;
    private static final int F_CHIP = 10;
    private static final int F_LOOKS = 13;
    private static final int F_RECALL = 14;
    private static final int F_COUNT = 15;

    private final Inventory inventory;
    @Nullable
    private EditBox nameBox;
    private int seenVersion = -1;
    private int focus = -1;
    private int ticks;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventory = inventory;
    }

    @Nullable
    private static Payloads.CompanionSnapshot snap() {
        return ClientState.companion();
    }

    @Override
    protected void init() {
        super.init();
        this.nameBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 32, 104, 14,
                Component.translatable("gui.nerocompanion.companion.section.name"));
        this.nameBox.setMaxLength(24);
        this.nameBox.setHint(Component.translatable("gui.nerocompanion.companion.name.hint"));
        Payloads.CompanionSnapshot s = snap();
        this.nameBox.setValue(s == null ? "" : s.name());
        this.addRenderableWidget(this.nameBox);
        this.seenVersion = ClientState.companionVersion();
    }

    @Override
    @Nullable
    protected EditBox typingBox() {
        return this.nameBox;
    }

    @Override
    public Component getTitle() {
        return this.title();
    }

    private Component title() {
        Payloads.CompanionSnapshot s = snap();
        if (s == null) {
            return Component.translatable("gui.nerocompanion.companion.menu");
        }
        return Component.translatable("gui.nerocompanion.companion.title",
                ClientText.displayName(s.name(), s.chassis()), ClientText.typeName(s.chassis()));
    }

    @Override
    protected Component subtitle() {
        return Component.translatable("gui.nerocompanion.companion.subtitle");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.ticks++;
        if (ClientState.companionVersion() != this.seenVersion) {
            this.seenVersion = ClientState.companionVersion();
            Payloads.CompanionSnapshot s = snap();
            this.stopWaiting();
            if (s != null) {
                this.showFeedback(s.feedback());
                if (this.nameBox != null && !this.nameBox.isFocused()) {
                    this.nameBox.setValue(s.name());
                }
            }
        }
    }

    // =================================================================================================
    // State helpers
    // =================================================================================================

    private boolean away(Payloads.CompanionSnapshot s) {
        return s.has(Payloads.CompanionSnapshot.AWAY) || !s.has(Payloads.CompanionSnapshot.LOADED);
    }

    /** Why a mode button is disabled, or null. */
    @Nullable
    private Component modeDenial(Payloads.CompanionSnapshot s, CompanionMode mode) {
        if (this.isWaiting()) {
            return Component.translatable("gui.nerocompanion.waiting");
        }
        if (this.away(s)) {
            return Component.translatable("gui.nerocompanion.reason.away");
        }
        Payloads.ModeState state = s.modeState(mode);
        if (state.state() == Payloads.ModeState.TYPE_CANT) {
            return Component.translatable("gui.nerocompanion.reason.type", ClientText.typeName(s.chassis()));
        }
        if (state.state() == Payloads.ModeState.NEEDS_CHIP) {
            String path = state.chip().map(id -> id.getPath()).orElse("guard_protocol_chip");
            return Component.translatable("gui.nerocompanion.reason.chip",
                    Component.translatable("item.nerocompanion." + path));
        }
        if (s.has(Payloads.CompanionSnapshot.LOW_BATTERY) && mode.isActiveDrain()) {
            return Component.translatable("gui.nerocompanion.reason.low_battery");
        }
        return null;
    }

    private int slotX(int slots, int k) {
        return 208 - 20 * (slots - 1 - k);
    }

    private Component energyLine(Payloads.CompanionSnapshot s) {
        Component third;
        if (s.has(Payloads.CompanionSnapshot.CHARGING) && s.chargingFePerTick() > 0) {
            double minutes = (s.capacityFe() - s.energyFe()) / (double) (s.chargingFePerTick() * 20L) / 60.0D;
            third = Component.translatable("gui.nerocompanion.energy.charging", ClientText.duration(minutes));
        } else if (s.has(Payloads.CompanionSnapshot.SLEEPING) || s.mode() == CompanionMode.SLEEP) {
            third = Component.translatable("gui.nerocompanion.energy.sleep");
        } else if (s.has(Payloads.CompanionSnapshot.LOW_BATTERY)) {
            third = Component.translatable("gui.nerocompanion.energy.low");
        } else {
            long drain = s.mode().isActiveDrain() ? s.drainActiveFePerSec() : s.drainIdleFePerSec();
            double minutes = drain <= 0 ? 0 : s.energyFe() / (double) drain / 60.0D;
            third = Component.translatable("gui.nerocompanion.energy.left", ClientText.duration(minutes),
                    ClientText.modeName(s.mode()));
        }
        return Component.translatable("gui.nerocompanion.companion.energy.line", UiFormat.fe(s.energyFe()),
                UiFormat.fe(s.capacityFe()), third);
    }

    private static int categoryIndex(ChipCategory category) {
        return category.ordinal();
    }

    @Nullable
    private static ChipCategory singleCategory(int mask) {
        ChipCategory only = null;
        for (ChipCategory category : ChipCategory.values()) {
            if ((mask & (1 << category.ordinal())) != 0) {
                if (only != null) {
                    return null;
                }
                only = category;
            }
        }
        return only;
    }

    // =================================================================================================
    // Drawing
    // =================================================================================================

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        Payloads.CompanionSnapshot s = snap();
        if (s == null) {
            this.text(g, Component.translatable("gui.nerocompanion.waiting"), x + 8, y + 32, CompanionColours.SUBTLE);
            return;
        }
        boolean away = this.away(s);
        this.text(g, Component.translatable("gui.nerocompanion.companion.section.job"), x + 132, y + 30,
                CompanionColours.SUBTLE);

        // 2 save-name tick
        boolean same = this.nameBox == null || this.nameBox.getValue().strip().equals(s.name());
        boolean saveOn = !same && !this.isWaiting();
        boolean saveHot = inside(mx, my, x + 114, y + 32, 14, 14) || this.focus == F_SAVE;
        this.button(g, x + 114, y + 32, 14, 14, Component.empty(), saveOn, saveHot, false, false);
        Sheet.blit(g, x + 115, y + 33, 12, 12, 240, 32, 16, 16, saveOn ? Sheet.WHITE : 0x80FFFFFF);
        if (inside(mx, my, x + 114, y + 32, 14, 14)) {
            this.tooltip(saveOn ? Component.translatable("gui.nerocompanion.companion.name.save.tip")
                    : Component.translatable(this.isWaiting() ? "gui.nerocompanion.waiting"
                            : "gui.nerocompanion.reason.same_name"));
        }
        if (this.nameBox != null && this.nameBox.isMouseOver(mx, my)) {
            this.tooltip(Component.translatable("gui.nerocompanion.companion.name.tip"));
        }

        // 3 type line
        Component typeLine = Component.translatable("gui.nerocompanion.companion.typeline",
                ClientText.traitName(s.trait()), ClientText.typeName(s.chassis()));
        this.text(g, typeLine, x + 8, y + 50, CompanionColours.SUBTLE);
        if (inside(mx, my, x + 8, y + 49, this.font.width(typeLine), 10)) {
            this.tooltip(Component.translatable("gui.nerocompanion.companion.trait.tip."
                    + ClientText.traitId(s.trait())));
        }

        // 4/5 energy
        this.text(g, Component.translatable("gui.nerocompanion.companion.section.energy"), x + 8, y + 56,
                CompanionColours.SUBTLE);
        int pct = s.capacityFe() <= 0 ? 0 : (int) Math.min(100L, s.energyFe() * 100L / s.capacityFe());
        this.bar(g, x + 8, y + 64, 112, pct, away, s.has(Payloads.CompanionSnapshot.SLEEPING),
                s.has(Payloads.CompanionSnapshot.LOW_BATTERY), s.has(Payloads.CompanionSnapshot.CHARGING));
        Component line = this.energyLine(s);
        this.text(g, this.fit(line.getString(), 118), x + 8, y + 72, CompanionColours.TITLE);
        if (inside(mx, my, x + 8, y + 63, 112, 18)) {
            this.tooltip(Component.translatable("gui.nerocompanion.companion.energy.tip"));
        }

        // 6 bond
        this.text(g, Component.translatable("gui.nerocompanion.companion.section.bond"), x + 8, y + 84,
                CompanionColours.SUBTLE);
        for (int i = 0; i < 4; i++) {
            boolean lit = i < s.bondLevel();
            Sheet.blit(g, x + 40 + 10 * i, y + 84, lit ? 8 : 0, 48, 8, 8);
        }
        if (inside(mx, my, x + 40, y + 84, 38, 8)) {
            Component next = s.bondLevel() >= 4 ? Component.translatable("gui.nerocompanion.bond.max")
                    : Component.translatable("gui.nerocompanion.bond.next." + (s.bondLevel() + 1));
            this.tooltip(Component.translatable("gui.nerocompanion.companion.bond.tip", s.bondLevel(), next));
        }

        // 7 modes
        CompanionMode[] modes = CompanionMode.values();
        for (int i = 0; i < modes.length; i++) {
            CompanionMode mode = modes[i];
            int bx = x + MODE_X[i % 2];
            int by = y + MODE_Y[i / 2];
            Component denial = this.modeDenial(s, mode);
            boolean current = mode == s.mode();
            boolean hot = inside(mx, my, bx, by, 52, 16) || this.focus == F_MODE + i;
            this.button(g, bx, by, 52, 16, Component.empty(), denial == null, hot && denial == null, false, current);
            Sheet.tinted(g, bx + 3, by + 4, 32 + 8 * i, 48, 8, 8, denial == null ? Sheet.WHITE : 0x808A93A6);
            this.text(g, this.fit(ClientText.modeName(mode).getString(), 36), bx + 14, by + 4,
                    denial == null ? CompanionColours.TITLE : CompanionColours.DISABLED_TEXT);
            if (inside(mx, my, bx, by, 52, 16)) {
                this.tooltip(denial != null ? List.of(ClientText.modeName(mode), denial)
                        : List.of(ClientText.modeName(mode),
                                Component.translatable("mode.nerocompanion." + mode.id() + ".tip")));
            }
        }

        // 8 chips
        this.text(g, Component.translatable("gui.nerocompanion.companion.section.chips"), x + 132, y + 116,
                CompanionColours.SUBTLE);
        int slots = Math.max(0, Math.min(3, s.chipSlots()));
        ChipCategory single = singleCategory(s.chipCategoryMask());
        for (int k = 0; k < slots; k++) {
            int sx = x + this.slotX(slots, k);
            int sy = y + CHIP_Y;
            boolean hot = inside(mx, my, sx, sy, 18, 18) || this.focus == F_CHIP + k;
            if (k < s.chips().size()) {
                Payloads.ChipView chip = s.chips().get(k);
                Optional<ChipKind> kind = ChipKinds.byId(chip.chip());
                int cat = kind.map(value -> categoryIndex(value.category())).orElse(0);
                Sheet.blit(g, sx, sy, 18 + 18 * cat, 80, 18, 18);
                Item item = kind.map(value -> ModItems.chip(value, chip.tier())).orElse(null);
                if (item != null) {
                    g.item(new ItemStack(item), sx + 1, sy + 1);
                }
                if (inside(mx, my, sx, sy, 18, 18)) {
                    List<Component> lines = new ArrayList<>();
                    if (item != null) {
                        lines.add(new ItemStack(item).getHoverName());
                        ChipItem.describe(kind.get(), chip.tier(), lines::add);
                    }
                    lines.add(Component.translatable("gui.nerocompanion.chip.remove.tip"));
                    this.tooltip(lines);
                }
            } else {
                if (single != null) {
                    Sheet.blit(g, sx, sy, 18 + 18 * categoryIndex(single), 80, 18, 18);
                } else {
                    Sheet.blit(g, sx, sy, 0, 80, 18, 18);
                }
                if (inside(mx, my, sx, sy, 18, 18)) {
                    if (single != null) {
                        Component cat = Component.translatable("chip.nerocompanion.category." + single.id());
                        this.tooltip(Component.translatable("gui.nerocompanion.chip.empty_cat.tip", cat, cat));
                    } else {
                        this.tooltip(Component.translatable("gui.nerocompanion.chip.empty.tip"));
                    }
                }
            }
            if (hot) {
                Sheet.blit(g, sx, sy, 90, 80, 18, 18);
            }
        }
        if (slots > 0) {
            this.text(g, Component.translatable("gui.nerocompanion.chip.legend", s.chips().size(), slots), x + 132,
                    y + 130, CompanionColours.SUBTLE);
        }

        // 9-10 Skin Studio (skins and hats), recall
        boolean looksOn = !away && !this.isWaiting();
        boolean recallOn = s.has(Payloads.CompanionSnapshot.LOADED) && !this.isWaiting();
        this.iconButton(g, mx, my, x + 8, y + 98, 76, 16, 176,
                Component.translatable("gui.nerocompanion.companion.studio"), looksOn, false, this.focus == F_LOOKS,
                looksOn ? "gui.nerocompanion.companion.studio.tip" : "gui.nerocompanion.reason.away");
        this.iconButton(g, mx, my, x + 88, y + 98, 40, 16, 128,
                Component.translatable("gui.nerocompanion.companion.recall"), recallOn, true,
                this.focus == F_RECALL,
                recallOn ? "gui.nerocompanion.companion.recall.tip" : "gui.nerocompanion.reason.away");
    }

    private void iconButton(GuiGraphicsExtractor g, int mx, int my, int bx, int by, int w, int h, int iconU,
            Component label, boolean enabled, boolean primary, boolean focused, String tipKey) {
        boolean hot = inside(mx, my, bx, by, w, h);
        this.button(g, bx, by, w, h, Component.empty(), enabled, (hot || focused) && enabled, primary, false);
        Sheet.blit(g, bx + 2, by + 4, 8, 8, iconU, 32, 16, 16, enabled ? Sheet.WHITE : 0x80FFFFFF);
        String fitted = this.fit(label.getString(), w - 13);
        this.text(g, fitted, bx + 11, by + 4, enabled ? CompanionColours.TITLE : CompanionColours.DISABLED_TEXT);
        if (hot) {
            this.tooltip(Component.translatable(tipKey));
        }
    }

    /** A 3-sliced trough with the state's fill (shared with the Bay and HUD rules). */
    void bar(GuiGraphicsExtractor g, int bx, int by, int w, int pct, boolean offline, boolean sleeping, boolean low,
            boolean charging) {
        Sheet.threeSlice(g, bx, by, w, offline ? 128 : 0, 64, 64, 6, 2);
        if (offline) {
            return;
        }
        int fill = UiFormat.fill(pct, w - 2);
        int u;
        int v = 72;
        if (sleeping) {
            u = 64;
            v = 64;
        } else if (pct >= 100) {
            u = 192;
        } else if (low || pct <= 25) {
            u = 128;
        } else if (pct <= 50) {
            u = 64;
        } else {
            u = 0;
        }
        Sheet.blit(g, bx + 1, by + 1, fill, 4, u, v, Math.min(fill, 64), 4, Sheet.WHITE);
        if (charging && fill > 0 && (this.ticks / 10) % 2 == 0) {
            g.fill(bx + fill, by + 1, bx + fill + 1, by + 5, CompanionColours.TEAL_BRIGHT);
        }
    }

    // =================================================================================================
    // Input
    // =================================================================================================

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
        clickSound();
        this.startWaiting();
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        Payloads.CompanionSnapshot s = snap();
        if (s == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        int x = this.leftPos;
        int y = this.topPos;
        if (inside(mx, my, x + 114, y + 32, 14, 14)) {
            return this.activate(F_SAVE, s);
        }
        for (int i = 0; i < 8; i++) {
            if (inside(mx, my, x + MODE_X[i % 2], y + MODE_Y[i / 2], 52, 16)) {
                return this.activate(F_MODE + i, s);
            }
        }
        int slots = Math.max(0, Math.min(3, s.chipSlots()));
        for (int k = 0; k < slots; k++) {
            if (inside(mx, my, x + this.slotX(slots, k), y + CHIP_Y, 18, 18)) {
                return this.activate(F_CHIP + k, s);
            }
        }
        if (inside(mx, my, x + 8, y + 98, 76, 16)) {
            return this.activate(F_LOOKS, s);
        }
        if (inside(mx, my, x + 88, y + 98, 40, 16)) {
            return this.activate(F_RECALL, s);
        }
        return false;
    }

    /** Runs the control at focus index {@code index}; true when the input is consumed. */
    private boolean activate(int index, Payloads.CompanionSnapshot s) {
        if (this.isWaiting()) {
            return true;
        }
        boolean away = this.away(s);
        if (index == F_SAVE) {
            if (this.nameBox != null && !this.nameBox.getValue().strip().equals(s.name())) {
                this.send(new Payloads.Rename(s.companionId(), this.nameBox.getValue()));
            }
            return true;
        }
        if (index >= F_MODE && index < F_MODE + 8) {
            CompanionMode mode = CompanionMode.values()[index - F_MODE];
            Component denial = this.modeDenial(s, mode);
            if (denial != null) {
                this.deny(denial);
            } else if (mode != s.mode()) {
                this.send(new Payloads.SetMode(s.companionId(), mode, false));
            }
            return true;
        }
        if (index >= F_CHIP && index < F_CHIP + 3) {
            int k = index - F_CHIP;
            if (away) {
                this.deny(Component.translatable("gui.nerocompanion.reason.away"));
            } else if (k < s.chips().size()) {
                this.send(new Payloads.ChipEdit(s.companionId(), k, false));
            } else if (this.inventory.player.getMainHandItem().getItem() instanceof ChipItem) {
                this.send(new Payloads.ChipEdit(s.companionId(), k, true));
            } else {
                this.hint(Component.translatable("gui.nerocompanion.reason.empty_hand"));
            }
            return true;
        }
        if (index == F_LOOKS) {
            if (away) {
                this.deny(Component.translatable("gui.nerocompanion.reason.away"));
            } else {
                clickSound();
                za.co.neroland.nerocompanion.client.Screens.set(new SkinStudioScreen(this.menu, this.inventory,
                        this.title));
            }
            return true;
        }
        if (index == F_RECALL) {
            if (!s.has(Payloads.CompanionSnapshot.LOADED)) {
                this.deny(Component.translatable("gui.nerocompanion.reason.away"));
            } else {
                this.send(new Payloads.Recall(s.companionId()));
            }
            return true;
        }
        if (index == F_NAME && this.nameBox != null) {
            this.setFocused(this.nameBox);
            return true;
        }
        return false;
    }

    @Override
    protected boolean key(int key) {
        Payloads.CompanionSnapshot s = snap();
        if (s == null) {
            return false;
        }
        boolean typing = this.nameBox != null && this.nameBox.isFocused();
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && typing) {
            return this.activate(F_SAVE, s);
        }
        if (key == InputConstants.KEY_TAB) {
            this.focus = (this.focus + 1) % F_COUNT;
            if (this.nameBox != null) {
                this.nameBox.setFocused(this.focus == F_NAME);
                this.setFocused(this.focus == F_NAME ? this.nameBox : null);
            }
            return true;
        }
        if (typing) {
            return false;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) && this.focus >= 0) {
            return this.activate(this.focus, s);
        }
        if (this.focus >= F_MODE && this.focus < F_MODE + 8) {
            int i = this.focus - F_MODE;
            int col = i % 2;
            int row = i / 2;
            if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
                col = 1 - col;
            } else if (key == InputConstants.KEY_UP) {
                row = Math.floorMod(row - 1, 4);
            } else if (key == InputConstants.KEY_DOWN) {
                row = Math.floorMod(row + 1, 4);
            } else {
                return false;
            }
            this.focus = F_MODE + row * 2 + col;
            return true;
        }
        return false;
    }
}
