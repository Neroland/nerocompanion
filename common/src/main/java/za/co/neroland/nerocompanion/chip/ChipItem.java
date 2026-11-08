package za.co.neroland.nerocompanion.chip;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.upgrade.UpgradeContainer;
import za.co.neroland.nerolandcore.upgrade.UpgradeType;

import za.co.neroland.nerocompanion.entity.ChipStack;

/**
 * A companion chip item: one kind at one tier. Installed by holding it and clicking a chip slot on the Companion
 * screen (or right-clicking your companion with it); the server validates every insert.
 *
 * <p>{@link #CLASSIFIER} is the NeroCompanion classifier for Core's {@link UpgradeContainer}: the four stat
 * families that are Core concepts report their Core {@link UpgradeType}; every other chip reports none.
 */
public class ChipItem extends Item {

    /** Core classifier: a chip's Core upgrade family, or null. */
    public static final UpgradeContainer.Classifier CLASSIFIER = ChipItem::coreTypeOf;

    private static final String[] NUMERALS = {"I", "II", "III"};

    private final ChipKind kind;
    private final int tier;

    public ChipItem(Properties properties, ChipKind kind, int tier) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    public ChipKind kind() {
        return this.kind;
    }

    public int tier() {
        return this.tier;
    }

    public ChipStack toChipStack() {
        return new ChipStack(this.kind.id(), this.tier);
    }

    @Nullable
    public static UpgradeType coreTypeOf(ItemStack stack) {
        return stack.getItem() instanceof ChipItem chip ? chip.kind.coreType().orElse(null) : null;
    }

    /** The roman numeral for a tier (1..3). */
    public static String numeral(int tier) {
        return NUMERALS[Math.max(1, Math.min(3, tier)) - 1];
    }

    /** The tier's magnitude for the effect sentence (e.g. {@code 25} for Capacity II). */
    public static int magnitude(ChipKind kind, int tier) {
        if (kind.equals(ChipKinds.SPEED)) {
            return 10 * tier;
        }
        if (kind.equals(ChipKinds.EFFICIENCY)) {
            return new int[] {15, 25, 35}[tier - 1];
        }
        if (kind.equals(ChipKinds.CAPACITY)) {
            return new int[] {25, 50, 75}[tier - 1];
        }
        if (kind.equals(ChipKinds.RANGE) || kind.equals(ChipKinds.ARMOUR)) {
            return 2 * tier;
        }
        if (kind.equals(ChipKinds.VITALITY)) {
            return 4 * tier;
        }
        return 0;
    }

    /** The tooltip lines of a chip, shared with the Companion screen's filled-slot tooltip. */
    public static void describe(ChipKind kind, int tier, Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("chip.nerocompanion.line",
                Component.translatable("chip.nerocompanion.category." + kind.category().id()),
                numeral(tier)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("chip.nerocompanion." + kind.name() + ".effect", magnitude(kind, tier))
                .withStyle(ChatFormatting.DARK_AQUA));
        kind.coreType().ifPresent(core -> tooltip.accept(Component.translatable("chip.nerocompanion.core",
                Component.translatable("chip.nerocompanion.core." + core.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.DARK_GRAY)));
    }

    @Override
    //? if >=1.21.5 {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        Consumer<Component> tooltip = lines::add;
    *///?}
        describe(this.kind, this.tier, tooltip);
    }
}
