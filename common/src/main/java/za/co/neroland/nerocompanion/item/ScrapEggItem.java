package za.co.neroland.nerocompanion.item;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.incubation.Incubators;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The Scrap Egg: a riveted shell that hatches a Scrapling after five minutes on a Charging Pad or in a Companion
 * Bay. Use it on a Pad (or sneak-use it on a Pad or Bay; a plain click on a Bay opens the Bay) to set it incubating;
 * the player who sets it down owns what hatches. Using an egg on an incubator that already holds one of yours shows
 * its progress.
 *
 * <p>The item itself carries only progress ({@value #TICKS_KEY} in vanilla custom data, kept when an incubator is
 * broken), never an owner: the owner is the player who places it, so an egg handed over or found hatches for its
 * finder.
 */
public class ScrapEggItem extends Item {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "scrap_egg");
    public static final String TICKS_KEY = "incubation_ticks";
    private static final Codec<Integer> TICKS = Codec.INT.fieldOf(TICKS_KEY).codec();

    public ScrapEggItem(Properties properties) {
        super(properties);
    }

    /** One Scrap Egg (air if the item is not registered). */
    public static ItemStack newStack() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(ID));
    }

    /** Incubation progress the egg carries from an earlier incubator (0 for a new egg). */
    public static int carriedTicks(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        return Incubation.clamp(TICKS.parse(NbtOps.INSTANCE, data.copyTag()).result().orElse(0));
    }

    /** {@code stack} carrying {@code ticks} of progress (0 clears it). */
    public static ItemStack withTicks(ItemStack stack, int ticks) {
        int clamped = Incubation.clamp(ticks);
        if (clamped <= 0) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return stack;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(TICKS_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!Incubators.isIncubator(context.getLevel().getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (Services.ENTITIES.isFakePlayer(player)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        Incubators.Placed placed = Incubators.place(level, context.getClickedPos(), player, stack);
        if (placed == Incubators.Placed.PROGRESS) {
            int pct = Incubators.at(level, context.getClickedPos())
                    .map(egg -> Math.round(egg.progress() * 100.0F)).orElse(0);
            player.sendSystemMessage(Component.translatable(placed.key(), pct), true);
        } else if (placed == Incubators.Placed.TOO_MANY) {
            player.sendSystemMessage(Component.translatable(placed.key(), Incubators.MAX_EGGS_PER_PLAYER), true);
        } else {
            player.sendSystemMessage(Component.translatable(placed.key()), true);
        }
        if (placed.consumed() && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
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
        tooltip.accept(Component.translatable("item.nerocompanion.scrap_egg.tip").withStyle(ChatFormatting.GRAY));
        int ticks = carriedTicks(stack);
        if (ticks > 0) {
            tooltip.accept(Component.translatable("item.nerocompanion.scrap_egg.progress",
                    Math.round(Incubation.progressOf(ticks) * 100.0F)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
