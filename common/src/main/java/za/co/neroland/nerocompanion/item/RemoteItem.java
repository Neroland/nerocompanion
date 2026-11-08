package za.co.neroland.nerocompanion.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.2 {
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemUseAnimation;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
*///?}
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.level.Level;

import za.co.neroland.nerocompanion.ClientBridge;

/**
 * The Remote. Tap use on your companion: its screen. Hold use (4 ticks or more): the job radial for the companion
 * under the crosshair (else the HUD's highlighted one). Sneak-use on your companion: recall it into a Capsule
 * (handled by the companion, server side). The item itself decides nothing: the client sends intents and the
 * server validates each one. Sneak-use on a block goes to the {@link RemoteBlockActions} handlers on the server
 * (container binding, mining corners) and never opens the radial.
 */
public class RemoteItem extends Item {

    /** Ticks the use key must be held before the radial opens. */
    public static final int HOLD_TICKS = 4;
    private static final int USE_DURATION = 72_000;

    public RemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    //? if >=1.21.2 {
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    //?} else {
    /*@Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    *///?}

    /** Sneak-use on a block: the server asks {@link RemoteBlockActions}; either way the hold radial stays shut. */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (context.getLevel() instanceof net.minecraft.server.level.ServerLevel server
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            RemoteBlockActions.dispatch(serverPlayer, server, context.getClickedPos(), context.getClickedFace(),
                    context.getItemInHand());
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide() && entity instanceof Player) {
            ClientBridge.get().remoteHeld(USE_DURATION - remaining);
        }
    }

    //? if >=1.21.2 {
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        this.released(level, entity, remaining);
        return true;
    }
    //?} else {
    /*@Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        this.released(level, entity, remaining);
    }
    *///?}

    private void released(Level level, LivingEntity entity, int remaining) {
        if (level.isClientSide() && entity instanceof Player && USE_DURATION - remaining < HOLD_TICKS) {
            ClientBridge.get().remoteTap();
        }
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
        tooltip.accept(Component.translatable("item.nerocompanion.remote.tip").withStyle(ChatFormatting.GRAY));
    }
}
