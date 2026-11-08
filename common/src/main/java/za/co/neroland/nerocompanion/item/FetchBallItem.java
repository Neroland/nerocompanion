package za.co.neroland.nerocompanion.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.2 {
import net.minecraft.world.InteractionResult;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
*///?}
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.level.Level;

import za.co.neroland.nerocompanion.fetch.FetchBallEntity;

/** The Fetch Ball: throw it and a pet companion brings it back (the fetch goal arrives in Stage 3). */
public class FetchBallItem extends Item {

    public FetchBallItem(Properties properties) {
        super(properties);
    }

    private static void throwBall(Level level, Player player, ItemStack stack) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.6F);
        if (level instanceof ServerLevel server) {
            FetchBallEntity ball = FetchBallEntity.thrown(server, player, stack.copyWithCount(1));
            server.addFreshEntity(ball);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        throwBall(level, player, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        throwBall(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    *///?}

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
        tooltip.accept(Component.translatable("item.nerocompanion.fetch_ball.tip").withStyle(ChatFormatting.GRAY));
    }
}
