package za.co.neroland.nerocompanion.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.ClientText;
import za.co.neroland.nerocompanion.client.CompanionColours;
import za.co.neroland.nerocompanion.client.gui.Sheet;
import za.co.neroland.nerocompanion.client.look.ClientLooks;
import za.co.neroland.nerocompanion.look.Look;
import za.co.neroland.nerocompanion.look.LookKind;
import za.co.neroland.nerocompanion.look.UnlockRule;
import za.co.neroland.nerocompanion.menu.CompanionMenu;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The Skin Studio (276x210, {@code 005} section 6): looks only, never abilities. Pushed over the Companion screen on
 * the same menu and snapshot; Esc / Back returns there. Cards come from the server's look catalogue
 * ({@code ClientLooks}) filtered to this type; which are unlocked comes from the owner's snapshot. Locked cards
 * always show, with the plain-words sentence of how to unlock them. The preview draws the live companion wearing
 * the selected card's look (client-side only until Apply). Client only.
 */
public class SkinStudioScreen extends CompanionScreenBase<CompanionMenu> {

    public static final int WIDTH = 276;
    public static final int HEIGHT = 210;
    /** Card columns; the third ends at 262 so the scroll arrows (x 265) sit clear of it. */
    private static final int[] CARD_X = {114, 164, 214};
    private static final int ARROW_X = 265;
    private static final int[] TAB_X = {114, 164};
    private static final int[] CARD_Y = {48, 100};
    /** Preview stage height: it ends at y 160, above the caption (165-173), the buttons (178-194) and the footer (196). */
    private static final int STAGE_H = 130;

    /** One card: id (empty = default skin / no hat), display name, unlocked, applied; the item an item rule uses. */
    private record Card(Optional<Identifier> id, Component name, boolean unlocked, boolean applied, int swatch,
            Component sentence, Optional<Identifier> useItem) {
    }

    private final Inventory inventory;
    private boolean hats;
    /**
     * The selected card, by look id (empty = the standard card), so the selection stays on the same look when the
     * cards re-sort (the applied card moves first after an Apply). {@code selectFirst}: whichever card is first.
     */
    private Optional<Identifier> selectedId = Optional.empty();
    private boolean selectFirst = true;
    private int scrollRow;
    private boolean paused;
    private float yaw;
    private int seenVersion = -1;

    /** Opens on the Skins tab; the Hats tab is one click (or Tab) away. */
    public SkinStudioScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventory = inventory;
        this.hats = false;
    }

    @Nullable
    private static Payloads.CompanionSnapshot snap() {
        return ClientState.companion();
    }

    @Override
    public Component getTitle() {
        Payloads.CompanionSnapshot s = snap();
        return Component.translatable("gui.nerocompanion.studio.title",
                s == null ? Component.empty() : ClientText.displayName(s.name(), s.chassis()));
    }

    @Override
    protected Component subtitle() {
        return Component.translatable("gui.nerocompanion.studio.subtitle");
    }

    @Override
    protected void escape() {
        clickSound();
        za.co.neroland.nerocompanion.client.Screens.set(new CompanionScreen(this.menu, this.inventory, this.title));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!this.paused) {
            this.yaw = (this.yaw + 1.0F) % 360.0F;
        }
        if (ClientState.companionVersion() != this.seenVersion) {
            this.seenVersion = ClientState.companionVersion();
            this.stopWaiting();
            Payloads.CompanionSnapshot s = snap();
            if (s != null) {
                this.showFeedback(s.feedback());
            }
        }
    }

    private int selectedIndex(List<Card> cards) {
        if (!this.selectFirst) {
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).id().equals(this.selectedId)) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** Selects the card at {@code index} (clamped) by its id and scrolls it into view. */
    private void select(List<Card> cards, int index) {
        int i = Math.max(0, Math.min(index, cards.size() - 1));
        this.selectedId = cards.isEmpty() ? Optional.empty() : cards.get(i).id();
        this.selectFirst = false;
        int row = i / 3;
        if (row < this.scrollRow) {
            this.scrollRow = row;
        } else if (row > this.scrollRow + 1) {
            this.scrollRow = row - 1;
        }
    }

    private void showTab(boolean hats) {
        this.hats = hats;
        this.selectFirst = true;
        this.selectedId = Optional.empty();
        this.scrollRow = 0;
    }

    /** Applied first, then unlocked (default first), then locked - alphabetical within a group. */
    private List<Card> cards(Payloads.CompanionSnapshot s) {
        List<Card> standard = new ArrayList<>();
        List<Card> unlocked = new ArrayList<>();
        List<Card> locked = new ArrayList<>();
        Identifier type = s.chassis().orElse(null);
        if (this.hats) {
            standard.add(new Card(Optional.empty(), Component.translatable("gui.nerocompanion.studio.no_hat"), true,
                    s.hat().isEmpty(), CompanionColours.ENERGY_MID, Component.translatable("gui.nerocompanion.unlock.done"),
                    Optional.empty()));
        } else {
            standard.add(new Card(Optional.empty(), Component.translatable("gui.nerocompanion.studio.default"), true,
                    s.skin().isEmpty() || s.skin().get().getPath().endsWith("/default"), CompanionColours.COMPANION_TEAL,
                    Component.translatable("gui.nerocompanion.unlock.done"), Optional.empty()));
        }
        if (type != null) {
            LookKind kind = this.hats ? LookKind.HAT : LookKind.SKIN;
            List<String> open = this.hats ? s.unlockedHats() : s.unlockedSkins();
            Optional<Identifier> current = this.hats ? s.hat() : s.skin();
            for (Look look : ClientLooks.forType(kind, type)) {
                if (look.isDefault()) {
                    continue;
                }
                boolean isUnlocked = open.contains(look.id().toString());
                boolean applied = current.isPresent() && current.get().equals(look.id());
                int swatch = look.swatch() >= 0 ? look.swatch() : look.eyeColor() >= 0 ? look.eyeColor()
                        : this.hats ? CompanionColours.ENERGY_MID : CompanionColours.COMPANION_TEAL;
                Optional<Identifier> item = look.unlock().kind() == UnlockRule.Kind.ITEM
                        ? Optional.ofNullable(Identifier.tryParse(look.unlock().arg())) : Optional.empty();
                Card card = new Card(Optional.of(look.id()), Component.translatable(look.nameKey()), isUnlocked,
                        applied, 0xFF000000 | swatch, isUnlocked ? Component.translatable("gui.nerocompanion.unlock.done")
                                : sentence(look), item);
                (applied ? standard : isUnlocked ? unlocked : locked).add(card);
            }
        }
        java.util.Comparator<Card> byName = java.util.Comparator.comparing(c -> c.name().getString());
        unlocked.sort(byName);
        locked.sort(byName);
        List<Card> cards = new ArrayList<>();
        // The applied card first: the standard card when nothing else is applied, else the applied look.
        standard.sort(java.util.Comparator.comparing(c -> !c.applied()));
        cards.addAll(standard);
        cards.addAll(unlocked);
        cards.addAll(locked);
        return cards;
    }

    /** The plain-words unlock sentence of a locked look ({@code 005} section 6.3): its own line, else the rule's. */
    private static Component sentence(Look look) {
        if (net.minecraft.locale.Language.getInstance().has(look.unlockKey())) {
            return Component.translatable(look.unlockKey());
        }
        UnlockRule rule = look.unlock();
        return switch (rule.kind()) {
            case DEFAULT -> Component.translatable("gui.nerocompanion.unlock.done");
            case BOND -> Component.translatable("gui.nerocompanion.unlock.bond", rule.level());
            case ADVANCEMENT -> Component.translatable("gui.nerocompanion.unlock.advancement", rule.arg());
            case ITEM -> Component.translatable("gui.nerocompanion.unlock.item", itemName(rule.arg()));
            case SHOP -> Component.translatable("gui.nerocompanion.unlock.shop_absent");
            case SEASON -> Component.translatable("gui.nerocompanion.unlock.season",
                    Component.translatable("gui.nerocompanion.season." + rule.arg()));
            case HOOK -> Component.translatable("gui.nerocompanion.unlock.hook", rule.arg());
        };
    }

    private static Component itemName(String id) {
        Identifier item = Identifier.tryParse(id);
        return item == null ? Component.literal(id)
                : Component.translatable("item." + item.getNamespace() + "." + item.getPath());
    }

    /** The loaded companion entity of this snapshot, for the preview, or null when it is away. */
    @Nullable
    private static net.minecraft.world.entity.LivingEntity previewEntity(Payloads.CompanionSnapshot s) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Payloads.HudEntry entry : ClientState.hud()) {
            if (entry.companionId().equals(s.companionId())) {
                net.minecraft.world.entity.Entity entity = mc.level.getEntity(entry.entityId());
                return entity instanceof net.minecraft.world.entity.LivingEntity living ? living : null;
            }
        }
        return null;
    }

    /** Draws the companion in the stage wearing the selected card's look; false when it cannot be drawn. */
    private boolean drawPreview(GuiGraphicsExtractor g, Payloads.CompanionSnapshot s, Card selected, int x0, int y0,
            int w, int h) {
        net.minecraft.world.entity.LivingEntity entity = previewEntity(s);
        if (entity == null) {
            return false;
        }
        String skin = this.hats ? s.skin().map(Identifier::toString).orElse("")
                : selected.id().map(Identifier::toString).orElse("");
        String hat = this.hats ? selected.id().map(Identifier::toString).orElse("")
                : s.hat().map(Identifier::toString).orElse("");
        float size = Math.max(0.3F, Math.max(entity.getBbHeight(), entity.getBbWidth()));
        int scale = Math.max(16, Math.min(120, Math.round(80.0F / size)));
        // The vanilla inventory figure turns to face a point: a moving point is the turntable (about +-35 degrees).
        float cx = x0 + w / 2.0F;
        float cy = y0 + h / 2.0F;
        float mouseX = cx - (float) Math.sin(Math.toRadians(this.yaw)) * 60.0F;
        za.co.neroland.nerocompanion.client.LookPreview.begin(entity.getId(), skin, hat);
        try {
            //? if >=26.1 {
            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0,
                    x0 + w, y0 + h, scale, 0.0625F, mouseX, cy, entity);
            //?} else {
            /*net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0,
                    x0 + w, y0 + h, scale, 0.0625F, mouseX, cy, entity);
            *///?}
        } finally {
            za.co.neroland.nerocompanion.client.LookPreview.end();
        }
        return true;
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        Payloads.CompanionSnapshot s = snap();
        if (s == null) {
            // No snapshot yet (or it was dropped): say so instead of an empty panel.
            this.text(g, Component.translatable("gui.nerocompanion.waiting"), x + 8, y + 32, CompanionColours.SUBTLE);
            return;
        }
        // 1 preview stage: the live companion wearing the selected look; the type glyph while it is away
        Sheet.nine(g, x + 8, y + 30, 100, STAGE_H, 144, 240, 3);
        List<Card> all = this.cards(s);
        Card chosen = all.get(this.selectedIndex(all));
        if (!this.drawPreview(g, s, chosen, x + 10, y + 32, 96, STAGE_H - 20)) {
            int glyph = ClientText.typeIndex(s.chassis());
            int size = 64;
            int sway = (int) Math.round(Math.sin(Math.toRadians(this.yaw)) * 6.0D);
            Sheet.blit(g, x + 8 + (100 - size) / 2 + sway, y + 30 + (STAGE_H - size) / 2 - 8, size, size, 16 * glyph, 32,
                    16, 16, Sheet.WHITE);
        }
        boolean pauseHot = inside(mx, my, x + 92, y + 146, 12, 12);
        Sheet.blit(g, x + 94, y + 148, this.paused ? 216 : 208, 240, 8, 8);
        if (inside(mx, my, x + 8, y + 30, 100, STAGE_H) && !pauseHot) {
            this.tooltip(Component.translatable("gui.nerocompanion.studio.preview.tip"));
        }
        // 2 caption
        Component caption = s.name().isEmpty() ? ClientText.typeName(s.chassis())
                : Component.translatable("gui.nerocompanion.companion.typeline", ClientText.displayName(s.name(),
                        s.chassis()), ClientText.typeName(s.chassis()));
        this.text(g, this.fitWords(caption.getString(), 100), x + 8, y + 165, CompanionColours.SUBTLE);
        // 3 tabs
        this.tab(g, mx, my, x + TAB_X[0], y + 30, Component.translatable("gui.nerocompanion.studio.tab.skins"), !this.hats,
                "gui.nerocompanion.studio.tab.skins.tip");
        this.tab(g, mx, my, x + TAB_X[1], y + 30, Component.translatable("gui.nerocompanion.studio.tab.hats"), this.hats,
                "gui.nerocompanion.studio.tab.hats.tip");
        // 4 cards
        List<Card> cards = this.cards(s);
        int selected = this.selectedIndex(cards);
        for (int i = 0; i < 6; i++) {
            int index = this.scrollRow * 3 + i;
            if (index >= cards.size()) {
                break;
            }
            Card card = cards.get(index);
            int cx = x + CARD_X[i % 3];
            int cy = y + CARD_Y[i / 3];
            boolean sel = index == selected;
            boolean hot = inside(mx, my, cx, cy, 48, 48);
            Sheet.nine(g, cx, cy, 48, 48, sel ? 128 : 0, 240, sel ? 3 : 4);
            if (hot) {
                g.fill(cx, cy, cx + 48, cy + 1, CompanionColours.EDGE_HOT);
            }
            int alpha = card.unlocked() ? 0xFF000000 : 0x80000000;
            g.fill(cx + 2, cy + 2, cx + 46, cy + 8, (card.swatch() & 0x00FFFFFF) | alpha);
            // Two lines, broken between words; a word too long for the card ends in an ellipsis (the full name is in
            // the tooltip and the detail line).
            List<String> lines = this.wrap(card.name().getString(), 42, 42);
            for (int l = 0; l < lines.size(); l++) {
                g.text(this.font, lines.get(l), cx + 3, cy + 10 + 10 * l,
                        card.unlocked() ? CompanionColours.TITLE : CompanionColours.SUBTLE, false);
            }
            if (card.applied()) {
                Sheet.blit(g, cx + 37, cy + 37, 168, 240, 8, 8);
            } else if (!card.unlocked()) {
                Sheet.blit(g, cx + 37, cy + 37, 160, 240, 8, 8);
            }
            if (hot) {
                this.tooltip(card.name(), card.applied() ? Component.translatable("gui.nerocompanion.unlock.applied")
                        : card.sentence());
            }
        }
        // 5 scroll arrows
        Sheet.blit(g, x + ARROW_X, y + 48, 176, 240, 8, 8);
        Sheet.blit(g, x + ARROW_X, y + 140, 184, 240, 8, 8);
        // 6/7 detail
        Card current = cards.get(selected);
        this.text(g, this.fitWords(current.name().getString(), 150), x + 116, y + 151, CompanionColours.TITLE);
        Component sentence = current.applied() ? Component.translatable("gui.nerocompanion.unlock.applied")
                : current.sentence();
        // Two full-width lines at a 9 px pitch end at y 177, just above the Back / Apply buttons (y 178).
        List<String> detail = this.wrap(sentence.getString(), 150, 150);
        for (int l = 0; l < detail.size(); l++) {
            g.text(this.font, detail.get(l), x + 116, y + 160 + 9 * l, CompanionColours.SUBTLE, false);
        }
        // 8 back, 9 apply
        boolean backHot = inside(mx, my, x + 8, y + 178, 48, 16);
        this.button(g, x + 8, y + 178, 48, 16, Component.translatable("gui.nerocompanion.studio.back"), true, backHot,
                false, false);
        if (backHot) {
            this.tooltip(Component.translatable("gui.nerocompanion.studio.back.tip"));
        }
        boolean away = (s.flags() & Payloads.CompanionSnapshot.AWAY) != 0;
        boolean applyOn = current.unlocked() && !current.applied() && !this.isWaiting() && !away;
        boolean applyHot = inside(mx, my, x + 204, y + 178, 64, 16);
        Component applyLabel = current.useItem().isPresent() && current.unlocked()
                ? Component.translatable("gui.nerocompanion.studio.apply_item", itemName(current.useItem().get().toString()))
                : Component.translatable("gui.nerocompanion.studio.apply");
        this.button(g, x + 204, y + 178, 64, 16, Component.literal(this.fit(applyLabel.getString(), 60)), applyOn,
                applyHot && applyOn, true, false);
        if (applyHot) {
            Component why;
            if (applyOn) {
                why = Component.translatable("gui.nerocompanion.studio.apply.tip",
                        ClientText.displayName(s.name(), s.chassis()));
            } else if (current.applied()) {
                why = Component.translatable("gui.nerocompanion.reason.already_applied");
            } else if (away) {
                why = Component.translatable("gui.nerocompanion.reason.away");
            } else if (!current.unlocked() && current.useItem().isPresent()) {
                why = Component.translatable("gui.nerocompanion.reason.need_item",
                        itemName(current.useItem().get().toString()));
            } else if (!current.unlocked()) {
                why = current.sentence();
            } else {
                why = Component.translatable("gui.nerocompanion.waiting");
            }
            this.tooltip(why);
        }
    }

    private void tab(GuiGraphicsExtractor g, int mx, int my, int tx, int ty, Component label, boolean active,
            String tip) {
        boolean hot = inside(mx, my, tx, ty, 48, 14);
        this.button(g, tx, ty, 48, 14, label, true, hot, false, active);
        if (hot) {
            this.tooltip(Component.translatable(tip));
        }
    }

    @Override
    protected boolean click(double mx, double my, int button) {
        Payloads.CompanionSnapshot s = snap();
        if (s == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        int x = this.leftPos;
        int y = this.topPos;
        if (inside(mx, my, x + 92, y + 146, 12, 12)) {
            this.paused = !this.paused;
            clickSound();
            return true;
        }
        if (inside(mx, my, x + TAB_X[0], y + 30, 48, 14) || inside(mx, my, x + TAB_X[1], y + 30, 48, 14)) {
            this.showTab(inside(mx, my, x + TAB_X[1], y + 30, 48, 14));
            clickSound();
            return true;
        }
        List<Card> cards = this.cards(s);
        for (int i = 0; i < 6; i++) {
            int index = this.scrollRow * 3 + i;
            if (index < cards.size() && inside(mx, my, x + CARD_X[i % 3], y + CARD_Y[i / 3], 48, 48)) {
                this.select(cards, index);
                clickSound();
                return true;
            }
        }
        if (inside(mx, my, x + ARROW_X, y + 48, 8, 8)) {
            this.scrollRow = Math.max(0, this.scrollRow - 1);
            return true;
        }
        if (inside(mx, my, x + ARROW_X, y + 140, 8, 8)) {
            this.scrollRow = Math.min(Math.max(0, (cards.size() - 1) / 3 - 1), this.scrollRow + 1);
            return true;
        }
        if (inside(mx, my, x + 8, y + 178, 48, 16)) {
            this.escape();
            return true;
        }
        if (inside(mx, my, x + 204, y + 178, 64, 16)) {
            this.apply(s, cards);
            return true;
        }
        return false;
    }

    private void apply(Payloads.CompanionSnapshot s, List<Card> cards) {
        Card card = cards.get(this.selectedIndex(cards));
        if (this.isWaiting() || !card.unlocked() || card.applied()) {
            return;
        }
        Services.NETWORK.sendToServer(new Payloads.ApplySkin(s.companionId(), this.hats, card.id()));
        clickSound();
        this.startWaiting();
    }

    @Override
    protected boolean drag(double mx, double my, int button, double dx, double dy) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(mx, my, this.leftPos + 8, this.topPos + 30, 100, STAGE_H)) {
            this.yaw = (float) ((this.yaw + dx * 2.0D) % 360.0D);
            this.paused = true;
            return true;
        }
        return false;
    }

    @Override
    protected boolean scroll(double mx, double my, double amount) {
        Payloads.CompanionSnapshot s = snap();
        if (s == null) {
            return false;
        }
        int rows = (this.cards(s).size() + 2) / 3;
        this.scrollRow = Math.max(0, Math.min(Math.max(0, rows - 2), this.scrollRow - (int) Math.signum(amount)));
        return true;
    }

    @Override
    protected boolean key(int key) {
        Payloads.CompanionSnapshot s = snap();
        if (s == null) {
            return false;
        }
        List<Card> cards = this.cards(s);
        int sel = this.selectedIndex(cards);
        switch (key) {
            case InputConstants.KEY_LEFT -> this.select(cards, sel - 1);
            case InputConstants.KEY_RIGHT -> this.select(cards, sel + 1);
            case InputConstants.KEY_UP -> this.select(cards, sel - 3);
            case InputConstants.KEY_DOWN -> this.select(cards, sel + 3);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> this.apply(s, cards);
            case InputConstants.KEY_TAB -> this.showTab(!this.hats);
            default -> {
                return false;
            }
        }
        return true;
    }
}
