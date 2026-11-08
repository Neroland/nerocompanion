package za.co.neroland.nerocompanion.charge;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//? if >=1.21.2 {
import net.minecraft.world.level.block.state.properties.EnumProperty;
//?} else {
/*import net.minecraft.world.level.block.state.properties.DirectionProperty;
*///?}
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocompanion.menu.BayMenu;
import za.co.neroland.nerocompanion.menu.MenuOpener;
import za.co.neroland.nerocompanion.registry.ModBlockEntities;

/**
 * The Companion Bay: parks up to four companions (the entity leaves the world and its record waits here), charges
 * them, and is their home. Anyone may open it; the screen only ever shows the viewer's own companions in full.
 * Broken, it hands every parked companion back as a Capsule at the Bay - never lost, never doubled.
 */
public class CompanionBayBlock extends BaseEntityBlock {

    public static final MapCodec<CompanionBayBlock> CODEC = BlockCodecs.simple(CompanionBayBlock::new);
    //? if >=1.21.2 {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //?} else {
    /*public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    *///?}

    @SuppressWarnings("this-escape")
    public CompanionBayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // No @Override: 26.3 removed Block.codec().
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanionBayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.COMPANION_BAY.get(), AbstractMachineBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()
                && level instanceof net.minecraft.server.level.ServerLevel server
                && za.co.neroland.nerocompanion.incubation.Incubators.onEmptyHandUse(server, pos, serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CompanionBayBlockEntity bay) {
            if (MenuOpener.open(serverPlayer, BayMenu.provider(pos)).isPresent()) {
                bay.sendSnapshot(serverPlayer, za.co.neroland.nerocompanion.network.payload.Payloads.Feedback.NONE);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CompanionBayBlockEntity bay) {
            bay.handOutAll();
        }
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            za.co.neroland.nerocompanion.incubation.Incubators.incubatorRemoved(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
    *///?}
}
