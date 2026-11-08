package za.co.neroland.nerocompanion.carry;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.types.CarryBotEntity;
import za.co.neroland.nerocompanion.entity.types.MiningBotEntity;
import za.co.neroland.nerocompanion.mining.BotFocus;

/**
 * Remote sneak-use on a container: binds it as the chosen bot's delivery container (carry bots first, then mining
 * bots - see {@link BotFocus}), after {@code OwnerAuthority.mayOpen}. Using it again on the container the bot is
 * already bound to asks a carry bot for a deposit run now. {@code mayOpen} is asked again at every deposit.
 */
public final class ContainerBinding {

    private static final List<Class<? extends CompanionEntity>> KINDS = List.of(CarryBotEntity.class,
            MiningBotEntity.class);

    private ContainerBinding() {
    }

    public static InteractionResult onSneakUse(ServerPlayer player, ServerLevel level, BlockPos pos, Direction face,
            ItemStack remote) {
        if (!(level.getBlockEntity(pos) instanceof Container)
                && CargoOps.containerAt(level, HomePos.of(level, pos)) == null) {
            return InteractionResult.PASS; // neither a container nor (with NeroLogistics) a drive bay
        }
        Optional<CompanionEntity> picked = BotFocus.pick(player, level, pos, KINDS);
        if (picked.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.nerocompanion.carry.no_bot"), true);
            return InteractionResult.PASS;
        }
        CompanionEntity bot = picked.get();
        HomePos target = HomePos.of(level, pos);
        if (bot.boundContainer().filter(target::equals).isPresent()) {
            if (bot instanceof CarryBotEntity carry) {
                carry.requestDeposit();
                player.sendSystemMessage(Component.translatable("message.nerocompanion.carry.deposit_requested",
                        bot.getDisplayName()), true);
            } else {
                player.sendSystemMessage(Component.translatable("message.nerocompanion.carry.already_bound",
                        bot.getDisplayName()), true);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        Decision decision = DepositRules.bind(true, CargoOps.mayUse(bot, level, target));
        if (decision.denied()) {
            player.sendSystemMessage(Component.translatable("message.nerocompanion.carry.bind_denied",
                    bot.getDisplayName(), Component.translatable(decision.reasonKey())), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        bot.bindContainer(target);
        player.sendSystemMessage(Component.translatable("message.nerocompanion.carry.bound", bot.getDisplayName()),
                true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
