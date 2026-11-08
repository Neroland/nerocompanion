package za.co.neroland.nerocompanion.client.screen;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.ClientText;
import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.client.gui.Sheet;
import za.co.neroland.nerocompanion.client.gui.UiFormat;
import za.co.neroland.nerocompanion.menu.BayMenu;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The Companion Bay screen (248x196): four parked slots, the Bay's power, the viewer's companions close enough to
 * park, and their lost companions to reclaim. Only the viewer's own data is ever shown; someone else's parked
 * companion is a grey card with a type glyph and nothing else. Client only.
 */
public class CompanionBayScreen extends CompanionScreenBase<BayMenu> {

    public static final int WIDTH = 248;
    public static final int HEIGHT = 196;
    private static final int ROW = 14;
    private static final int LIST_Y = 130;
    private static final int LIST_H = 50;
    private static final int VISIBLE = 3;

    private int seenVersion = -1;
    private int nearbyScroll;
    private int lostScroll;
    private int focus = -1;

    public CompanionBayScreen(BayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Nullable
    private static Payloads.BaySnapshot snap() {
        return ClientState.bay();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.nerocompanion.bay.title");
    }

    @Override
    protected Component subtitle() {
        return Component.translatable("gui.nerocompanion.bay.subtitle");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (ClientState.bayVersion() != this.seenVersion) {
            this.seenVersion = ClientState.bayVersion();
            this.stopWaiting();
            Payloads.BaySnapshot s = snap();
            if (s != null) {
                this.showFeedback(s.feedback());
            }
        }
    }

    private int occupied(Payloads.BaySnapshot s) {
        int count = 0;
        for (Payloads.BaySlotView slot : s.slots()) {
            if (slot.kind() != Payloads.BaySlotView.EMPTY) {
                count++;
            }
        }
        return count;
    }

    private boolean atCap(Payloads.BaySnapshot s) {
        return s.liveCount() >= s.liveCap();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        Payloads.BaySnapshot s = snap();
        if (s == null) {
            return;
        }
        this.text(g, Component.translatable("gui.nerocompanion.bay.parked", this.occupied(s)), x + 8, y + 30,
                CompanionColours.SUBTLE);
        // 2/3 cards + unpark
        for (int k = 0; k < 4; k++) {
            Payloads.BaySlotView slot = k < s.slots().size() ? s.slots().get(k) : Payloads.BaySlotView.empty();
            int cx = x + 8 + 52 * k;
            int cy = y + 40;
            boolean cardHot = inside(mx, my, cx, cy, 48, 32);
            if (slot.kind() == Payloads.BaySlotView.MINE) {
                Sheet.blit(g, cx, cy, 48, 176, 48, 32);
                Sheet.blit(g, cx + 3, cy + 3, 16 * ClientText.typeIndex(slot.type()), 32, 16, 16);
                Component name = ClientText.displayName(slot.name(), slot.type());
                this.text(g, this.fit(name.getString(), 22), cx + 22, cy + 7, CompanionColours.TITLE);
                int fill = UiFormat.fill(slot.energyPct(), 40);
                Sheet.blit(g, cx + 4, cy + 23, fill, 4, 0, 72, Math.min(fill, 64), 4, Sheet.WHITE);
                int led = slot.state() == Payloads.BaySlotView.STATE_FULL ? CompanionColours.OK_GREEN
                        : slot.state() == Payloads.BaySlotView.STATE_CHARGING ? CompanionColours.COMPANION_TEAL
                                : CompanionColours.TEAL_DIM;
                Sheet.tinted(g, cx + 41, cy + 3, 112, 48, 4, 4, led);
                if (cardHot) {
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.slot.mine.tip", name, slot.energyPct(),
                            Component.translatable("gui.nerocompanion.bay.state." + switch (slot.state()) {
                                case Payloads.BaySlotView.STATE_CHARGING -> "charging";
                                case Payloads.BaySlotView.STATE_FULL -> "full";
                                default -> "parked";
                            })));
                }
            } else if (slot.kind() == Payloads.BaySlotView.FOREIGN) {
                Sheet.blit(g, cx, cy, 144, 176, 48, 32);
                Sheet.tinted(g, cx + 7, cy + 7, 32 + 8 * ClientText.typeIndex(slot.type()), 56, 8, 8, 0xFF8A93A6);
                if (cardHot) {
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.slot.foreign.tip"));
                }
            } else {
                Sheet.blit(g, cx, cy, 0, 176, 48, 32);
                if (cardHot) {
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.slot.empty.tip"));
                }
            }
            if (this.focus == k) {
                Sheet.blit(g, cx, cy, 192, 176, 48, 32);
            }
            // unpark button
            boolean mine = slot.kind() == Payloads.BaySlotView.MINE;
            boolean enabled = mine && !this.atCap(s) && !this.isWaiting();
            boolean hot = inside(mx, my, cx, y + 74, 48, 12);
            this.button(g, cx, y + 74, 48, 12, mine ? Component.translatable("gui.nerocompanion.bay.unpark")
                    : Component.literal("—"), enabled, (hot || this.focus == k) && enabled, enabled, false);
            if (hot) {
                if (enabled) {
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.unpark.tip"));
                } else if (!mine) {
                    this.tooltip(Component.translatable(slot.kind() == Payloads.BaySlotView.FOREIGN
                            ? "gui.nerocompanion.reason.bay_foreign" : "gui.nerocompanion.reason.slot_empty"));
                } else {
                    this.tooltip(this.isWaiting() ? Component.translatable("gui.nerocompanion.waiting")
                            : Component.translatable("gui.nerocompanion.reason.cap", s.liveCap()));
                }
            }
        }
        // 4 power
        this.text(g, Component.translatable("gui.nerocompanion.bay.power.section"), x + 8, y + 91,
                CompanionColours.SUBTLE);
        Sheet.threeSlice(g, x + 8, y + 100, 204, 0, 64, 64, 6, 2);
        int pct = s.bufferCapacityFe() <= 0 ? 0 : (int) Math.min(100L, s.bufferFe() * 100L / s.bufferCapacityFe());
        int fill = UiFormat.fill(pct, 202);
        for (int drawn = 0; drawn < fill; drawn += 64) {
            Sheet.blit(g, x + 9 + drawn, y + 101, Math.min(64, fill - drawn), 4, 0, 72, Math.min(64, fill - drawn), 4,
                    Sheet.WHITE);
        }
        int charging = 0;
        for (Payloads.BaySlotView slot : s.slots()) {
            if (slot.state() == Payloads.BaySlotView.STATE_CHARGING) {
                charging++;
            }
        }
        Component power = Component.translatable("gui.nerocompanion.bay.power", UiFormat.fe(s.bufferFe()),
                UiFormat.fe(s.bufferCapacityFe()), charging);
        if (s.bufferFe() <= 0) {
            power = Component.translatable("gui.nerocompanion.bay.power.with_empty", power,
                    Component.translatable("gui.nerocompanion.bay.power.empty"));
        }
        this.text(g, this.fit(power.getString(), 232), x + 8, y + 108, CompanionColours.TITLE);
        if (inside(mx, my, x + 8, y + 99, 204, 18)) {
            this.tooltip(Component.translatable("gui.nerocompanion.bay.power.tip"));
        }
        // 5 nearby list
        this.text(g, Component.translatable("gui.nerocompanion.bay.nearby"), x + 8, y + 120, CompanionColours.SUBTLE);
        Sheet.nine(g, x + 8, y + LIST_Y, 112, LIST_H, 144, 240, 3);
        this.list(g, mx, my, x + 8, s.nearby(), this.nearbyScroll, true, s.slots().size() > 0 && this.occupied(s) >= 4,
                Component.translatable("gui.nerocompanion.bay.nearby.none"));
        // 6 lost list
        this.text(g, Component.translatable("gui.nerocompanion.bay.lost"), x + 128, y + 120, CompanionColours.SUBTLE);
        Sheet.nine(g, x + 128, y + LIST_Y, 112, LIST_H, 144, 240, 3);
        this.list(g, mx, my, x + 128, s.lost(), this.lostScroll, false, false,
                Component.translatable("gui.nerocompanion.bay.lost.none"));
        // 7 arrows
        Sheet.blit(g, x + 112, y + LIST_Y, 176, 240, 8, 8);
        Sheet.blit(g, x + 112, y + LIST_Y + 42, 184, 240, 8, 8);
        Sheet.blit(g, x + 232, y + LIST_Y, 176, 240, 8, 8);
        Sheet.blit(g, x + 232, y + LIST_Y + 42, 184, 240, 8, 8);
    }

    private void list(GuiGraphicsExtractor g, int mx, int my, int lx, List<Payloads.BayListEntry> entries, int scroll,
            boolean nearby, boolean full, Component none) {
        int top = this.topPos + LIST_Y + 3;
        if (entries.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(none, 100);
            for (int l = 0; l < Math.min(3, lines.size()); l++) {
                g.text(this.font, lines.get(l), lx + 4, top + 2 + 10 * l, CompanionColours.SUBTLE, false);
            }
            return;
        }
        for (int r = 0; r < VISIBLE && scroll + r < entries.size(); r++) {
            Payloads.BayListEntry entry = entries.get(scroll + r);
            int ry = top + ROW * r;
            Sheet.blit(g, lx + 3, ry + 3, 32 + 8 * ClientText.typeIndex(entry.type()), 56, 8, 8);
            Component name = ClientText.displayName(entry.name(), entry.type());
            int buttonW = nearby ? 32 : 44;
            int bx = lx + 112 - 10 - buttonW;
            this.text(g, this.fit(name.getString(), nearby ? 56 : 48), lx + 13, ry + 3, CompanionColours.TITLE);
            boolean enabled = !this.isWaiting() && !(nearby && full);
            boolean hot = inside(mx, my, bx, ry + 1, buttonW, 12);
            this.button(g, bx, ry + 1, buttonW, 12, Component.translatable(nearby ? "gui.nerocompanion.bay.park"
                    : "gui.nerocompanion.bay.reclaim"), enabled, hot && enabled, true, false);
            if (hot) {
                if (!enabled) {
                    this.tooltip(Component.translatable(this.isWaiting() ? "gui.nerocompanion.waiting"
                            : "gui.nerocompanion.reason.bay_full"));
                } else if (nearby || entry.cost().isEmpty()) {
                    this.tooltip(Component.translatable(nearby ? "gui.nerocompanion.bay.park.tip"
                            : "gui.nerocompanion.bay.reclaim.tip"));
                } else {
                    // Say what it costs before the press (only when a cost applies on this server).
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.reclaim.tip"),
                            Component.translatable("gui.nerocompanion.bay.reclaim.cost", entry.cost()));
                }
            } else if (inside(mx, my, lx, ry, 112, ROW)) {
                if (nearby) {
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.nearby.tip", name, entry.energyPct()));
                } else {
                    this.tooltip(Component.translatable("gui.nerocompanion.bay.lost.tip", name, ago(entry.minutesAgo())));
                }
            }
        }
    }

    private static Component ago(long minutes) {
        if (minutes >= 24L * 60L) {
            return Component.translatable("gui.nerocompanion.time.ago.day", minutes / (24L * 60L));
        }
        if (minutes >= 60L) {
            return Component.translatable("gui.nerocompanion.time.ago.hour", minutes / 60L);
        }
        return Component.translatable("gui.nerocompanion.time.ago.min", minutes);
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        Payloads.BaySnapshot s = snap();
        if (s == null || button != InputConstants.MOUSE_BUTTON_LEFT || this.isWaiting()) {
            return false;
        }
        int x = this.leftPos;
        int y = this.topPos;
        for (int k = 0; k < 4 && k < s.slots().size(); k++) {
            if (inside(mx, my, x + 8 + 52 * k, y + 74, 48, 12)) {
                return this.unpark(s, k);
            }
        }
        int top = y + LIST_Y + 3;
        for (int r = 0; r < VISIBLE; r++) {
            int ry = top + ROW * r;
            if (inside(mx, my, x + 8 + 112 - 10 - 32, ry + 1, 32, 12) && this.nearbyScroll + r < s.nearby().size()) {
                if (this.occupied(s) >= 4) {
                    this.deny(Component.translatable("gui.nerocompanion.reason.bay_full"));
                    return true;
                }
                this.send(new Payloads.ParkUnpark(s.bay(), s.nearby().get(this.nearbyScroll + r).companionId(), true));
                return true;
            }
            if (inside(mx, my, x + 128 + 112 - 10 - 44, ry + 1, 44, 12) && this.lostScroll + r < s.lost().size()) {
                this.send(new Payloads.Reclaim(s.bay(), s.lost().get(this.lostScroll + r).companionId()));
                return true;
            }
        }
        if (inside(mx, my, x + 112, y + LIST_Y, 8, 8)) {
            this.nearbyScroll = Math.max(0, this.nearbyScroll - 1);
            return true;
        }
        if (inside(mx, my, x + 112, y + LIST_Y + 42, 8, 8)) {
            this.nearbyScroll = Math.max(0, Math.min(s.nearby().size() - VISIBLE, this.nearbyScroll + 1));
            return true;
        }
        if (inside(mx, my, x + 232, y + LIST_Y, 8, 8)) {
            this.lostScroll = Math.max(0, this.lostScroll - 1);
            return true;
        }
        if (inside(mx, my, x + 232, y + LIST_Y + 42, 8, 8)) {
            this.lostScroll = Math.max(0, Math.min(s.lost().size() - VISIBLE, this.lostScroll + 1));
            return true;
        }
        return false;
    }

    private boolean unpark(Payloads.BaySnapshot s, int k) {
        Payloads.BaySlotView slot = s.slots().get(k);
        if (slot.kind() != Payloads.BaySlotView.MINE) {
            this.deny(Component.translatable(slot.kind() == Payloads.BaySlotView.FOREIGN
                    ? "gui.nerocompanion.reason.bay_foreign" : "gui.nerocompanion.reason.slot_empty"));
        } else if (this.atCap(s)) {
            this.deny(Component.translatable("gui.nerocompanion.reason.cap", s.liveCap()));
        } else {
            this.send(new Payloads.ParkUnpark(s.bay(), slot.companionId(), false));
        }
        return true;
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Services.NETWORK.sendToServer(payload);
        clickSound();
        this.startWaiting();
    }

    @Override
    protected boolean scroll(double mx, double my, double amount) {
        Payloads.BaySnapshot s = snap();
        if (s == null) {
            return false;
        }
        int delta = -(int) Math.signum(amount);
        if (mx < this.leftPos + 124) {
            this.nearbyScroll = Math.max(0, Math.min(Math.max(0, s.nearby().size() - VISIBLE), this.nearbyScroll + delta));
        } else {
            this.lostScroll = Math.max(0, Math.min(Math.max(0, s.lost().size() - VISIBLE), this.lostScroll + delta));
        }
        return true;
    }

    @Override
    protected boolean key(int key) {
        Payloads.BaySnapshot s = snap();
        if (s == null) {
            return false;
        }
        if (key == InputConstants.KEY_TAB) {
            this.focus = (this.focus + 1) % 4;
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) && this.focus >= 0
                && this.focus < s.slots().size()) {
            return this.unpark(s, this.focus);
        }
        return false;
    }
}
