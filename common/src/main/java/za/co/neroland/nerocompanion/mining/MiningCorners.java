package za.co.neroland.nerocompanion.mining;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.types.MiningBotEntity;
import za.co.neroland.nerocompanion.network.Feedbacks;
import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * Remote sneak-use on a (non-container) block: mining corners. The first use marks corner 1 for the chosen bot
 * ({@link BotFocus}); the second marks corner 2, sets the job (clamped to {@code mining.maxJobSize}) and switches the
 * bot to {@code MINE}. Containers pass through to the carry binding. Picking a corner touches nothing in the world;
 * authority is checked per block when the bot actually breaks it.
 */
public final class MiningCorners {

    static final CornerPicks PICKS = new CornerPicks();

    /** Forgets {@code owner}'s pending corner pick (erasure). */
    public static void forget(java.util.UUID owner) {
        PICKS.clear(owner);
    }

    /** Server stopped: no player's pending pick survives into another world. */
    public static void serverStopped() {
        PICKS.clearAll();
    }

    private MiningCorners() {
    }

    public static InteractionResult onSneakUse(ServerPlayer player, ServerLevel level, BlockPos pos, Direction face,
            ItemStack remote) {
        if (level.getBlockEntity(pos) instanceof Container) {
            return InteractionResult.PASS;
        }
        // A pending first corner keeps its bot for the second, whatever the focus or distances say now.
        UUID pendingBot = PICKS.pendingFor(player.getUUID()).map(CornerPicks.Pending::botId).orElse(null);
        Optional<CompanionEntity> picked = BotFocus.pick(player, level, pos, List.of(MiningBotEntity.class),
                pendingBot);
        if (picked.isEmpty()) {
            PICKS.clear(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.nerocompanion.mine.no_bot"), true);
            return InteractionResult.PASS;
        }
        CompanionEntity bot = picked.get();
        Identifier dimension = level.dimension().identifier();
        CornerPicks.Pick pick = PICKS.pick(player.getUUID(), bot.getUUID(), dimension, pos, level.getGameTime());
        if (!pick.completed()) {
            player.sendSystemMessage(Component.translatable("message.nerocompanion.mine.corner_one",
                    bot.getDisplayName()), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos first = pick.first().orElse(pos);
        int edge = MineArea.clampEdge(NeroCompanionConfig.miningMaxJobSize());
        MineArea area = MineArea.of(first, pos, edge);
        // The job only exists if the bot actually goes into MINE (low battery / no MINE: nothing is left behind).
        Payloads.Feedback mode = bot.trySetMode(CompanionMode.MINE, player, false);
        if (!mode.ok()) {
            player.sendSystemMessage(Feedbacks.component(mode), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        bot.setJob(MineJob.start(dimension, area));
        if (bot instanceof MiningBotEntity miner) {
            miner.clearCrack();
        }
        String key = MineArea.wouldClamp(first, pos, edge) ? "message.nerocompanion.mine.area_clamped"
                : "message.nerocompanion.mine.area_set";
        player.sendSystemMessage(Component.translatable(key, bot.getDisplayName(), area.sizeX(), area.sizeY(),
                area.sizeZ(), edge), true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
