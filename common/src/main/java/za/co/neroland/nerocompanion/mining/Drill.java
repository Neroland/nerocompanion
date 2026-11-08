package za.co.neroland.nerocompanion.mining;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.registry.ModTags;

/**
 * The mining bot's iron drill (server): the best of an iron pickaxe, shovel and axe for speed; an iron tool's
 * harvest level for drops. See {@link MineRules} for the numbers.
 */
public final class Drill {

    /** The common ore tag the Ore Filter chip keeps. */
    public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("c", "ores"));

    /** Extra blocks the Ore Filter chip keeps (packs add to it; absent = empty). */
    public static final TagKey<Block> ORE_FILTER_EXTRA = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("nerocompanion", "ore_filter"));

    private static volatile ItemStack[] tools;

    private Drill() {
    }

    private static ItemStack[] tools() {
        ItemStack[] current = tools;
        if (current == null) {
            current = new ItemStack[] {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL),
                    new ItemStack(Items.IRON_AXE)};
            tools = current;
        }
        return current;
    }

    /** The drill head used for {@code state}: the fastest one that harvests it, else the fastest. */
    public static ItemStack toolFor(BlockState state) {
        ItemStack best = null;
        float bestSpeed = -1.0F;
        boolean bestHarvests = false;
        for (ItemStack tool : tools()) {
            float speed = tool.getDestroySpeed(state);
            boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
            if ((harvests && !bestHarvests) || (harvests == bestHarvests && speed > bestSpeed)) {
                best = tool;
                bestSpeed = speed;
                bestHarvests = harvests;
            }
        }
        return best;
    }

    public static boolean harvests(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        for (ItemStack tool : tools()) {
            if (tool.isCorrectToolForDrops(state)) {
                return true;
            }
        }
        return false;
    }

    public static boolean keptByOreFilter(BlockState state) {
        return state.is(ORES) || state.is(ORE_FILTER_EXTRA);
    }

    /** What the bot knows about one block before authority. Cheap: one state read, six fluid reads. */
    public static MineRules.Survey survey(ServerLevel level, BlockPos pos, boolean oreFilter) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.liquid()) {
            return MineRules.Survey.AIR;
        }
        // Next to any fluid (lava, or water that would flood the pit and float the bot off its work): leave it.
        boolean nearFluid = false;
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
                nearFluid = true;
                break;
            }
        }
        return new MineRules.Survey(false, state.is(ModTags.UNMINEABLE), oreFilter && !keptByOreFilter(state),
                harvests(state), nearFluid);
    }

    public static int breakTicks(ServerLevel level, BlockPos pos, BlockState state, List<ChipStack> chips) {
        ItemStack tool = toolFor(state);
        return MineRules.breakTicks(state.getDestroySpeed(level, pos), tool.getDestroySpeed(state), harvests(state),
                ChipEffects.speedMultiplier(chips));
    }

    public static boolean oreFilter(List<ChipStack> chips) {
        return ChipEffects.has(chips, ChipKinds.ORE_FILTER);
    }
}
