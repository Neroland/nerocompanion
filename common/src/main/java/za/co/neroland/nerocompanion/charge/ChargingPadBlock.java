package za.co.neroland.nerocompanion.charge;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.nerocompanion.registry.ModBlockEntities;

/**
 * The Charging Pad: a 2-px plate that takes Core energy from any side and charges the companion standing on it
 * (400 FE/t) and up to four more within 3 blocks (100 FE/t each). {@code charging} drives the ring; {@code powered}
 * says the buffer holds anything.
 */
public class ChargingPadBlock extends BaseEntityBlock {

    public static final MapCodec<ChargingPadBlock> CODEC = BlockCodecs.simple(ChargingPadBlock::new);
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    @SuppressWarnings("this-escape")
    public ChargingPadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGING, false).setValue(POWERED, false));
    }

    // No @Override: 26.3 removed Block.codec().
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGING, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Sneak-use with an empty hand checks or takes back an incubating Scrap Egg; otherwise nothing. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && player.isShiftKeyDown()
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level instanceof net.minecraft.server.level.ServerLevel server
                && za.co.neroland.nerocompanion.incubation.Incubators.onEmptyHandUse(server, pos, serverPlayer)) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingPadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CHARGING_PAD.get(), AbstractMachineBlockEntity::tick);
    }

    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server) {
            za.co.neroland.nerocompanion.incubation.Incubators.incubatorRemoved(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
    *///?}
}
