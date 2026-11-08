package za.co.neroland.nerocompanion.charge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
//? if >=1.21.2 {
import net.minecraft.world.level.block.state.properties.EnumProperty;
//?} else {
/*import net.minecraft.world.level.block.state.properties.DirectionProperty;
*///?}
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The Hand Crank Charger: the Core-only fallback. Right-click to crank: 60 FE into the companion standing on it,
 * one crank per 5 ticks per player, 0.05 food exhaustion per crank. Needs no power mod and no block entity;
 * {@code cranking} shows the handle turning for 10 ticks after each crank.
 */
public class HandCrankChargerBlock extends Block {

    public static final MapCodec<HandCrankChargerBlock> CODEC = BlockCodecs.simple(HandCrankChargerBlock::new);
    //? if >=1.21.2 {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //?} else {
    /*public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    *///?}
    public static final BooleanProperty CRANKING = BooleanProperty.create("cranking");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(2.0D, 2.0D, 2.0D, 14.0D, 12.0D, 14.0D));
    private static final int SHOW_TICKS = 10;

    /** Last crank game time per player; transient (never saved), pruned as it grows, cleared on server stop. */
    private static final Map<UUID, Long> LAST_CRANK = new HashMap<>();

    @SuppressWarnings("this-escape")
    public HandCrankChargerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CRANKING, false));
    }

    // No @Override: 26.3 removed Block.codec().
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CRANKING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (Services.ENTITIES.isFakePlayer(serverPlayer)) {
            // Automation cannot crank: a fake player pays no hunger, so it would be free energy.
            return InteractionResult.CONSUME;
        }
        long now = server.getGameTime();
        Long last = LAST_CRANK.get(serverPlayer.getUUID());
        if (last != null && !ChargeMath.crankReady(last, now)) {
            return InteractionResult.CONSUME;
        }
        List<CompanionEntity> on = level.getEntitiesOfClass(CompanionEntity.class,
                new AABB(pos).expandTowards(0.0D, 1.0D, 0.0D), CompanionEntity::isAlive);
        if (on.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("message.nerocompanion.crank.empty"), true);
            return InteractionResult.CONSUME;
        }
        if (LAST_CRANK.size() > 256) {
            LAST_CRANK.values().removeIf(tick -> now - tick > 200L);
        }
        LAST_CRANK.put(serverPlayer.getUUID(), now);
        CompanionEntity companion = on.get(0);
        long given = companion.receiveCharge(ChargeMath.crank(companion.energy().capacity()
                - companion.energy().stored()), ChargeMath.CRANK_FE / ChargeMath.CRANK_COOLDOWN_TICKS);
        serverPlayer.causeFoodExhaustion(ChargeMath.CRANK_EXHAUSTION);
        level.playSound(null, pos, za.co.neroland.nerocompanion.registry.ModSounds.CRANK_TURN.get(), SoundSource.BLOCKS, 0.6F, za.co.neroland.nerocompanion.registry.ModSounds.pitch(level.getRandom()));
        if (!state.getValue(CRANKING)) {
            level.setBlock(pos, state.setValue(CRANKING, true), 3);
        }
        server.scheduleTick(pos, this, SHOW_TICKS);
        serverPlayer.sendSystemMessage(Component.translatable("message.nerocompanion.crank.charged", given,
                companion.energy().percent()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(CRANKING)) {
            level.setBlock(pos, state.setValue(CRANKING, false), 3);
        }
    }

    /** Server stopped: forget every cooldown. */
    public static void serverStopped() {
        LAST_CRANK.clear();
    }
}
