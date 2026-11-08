package za.co.neroland.nerocompanion.charge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerolandcore.energy.EnergyConversions;
import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.registry.ModBlockEntities;

/**
 * The Charging Pad's buffer and charging loop. A Core energy block entity: energy arrives through Core's energy
 * capability on every face ({@link SidePreset#ALL_INPUT}, set explicitly - Core's builder defaults to all
 * disabled) and is held as NE; companions are paid in FE, converted at the boundary at the server's ratio. Runs
 * every {@value #PERIOD} ticks with that many ticks' worth of FE, so per-tick rates hold on average.
 */
public class ChargingPadBlockEntity extends AbstractMachineBlockEntity {

    static final int PERIOD = 5;

    private int phase;

    @SuppressWarnings("this-escape")
    public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGING_PAD.get(), pos, state, bufferNe(), Math.max(1, bufferNe() / 20), 1,
                stack -> null);
        this.installSideConfig(SideConfig.builder().channel(Channel.ENERGY).defaultPreset(SidePreset.ALL_INPUT).build());
    }

    private static int bufferNe() {
        return ChargeMath.bufferNe(ChargeMath.PAD_BUFFER_FE, EnergyConversions.neroToForgeRatio());
    }

    /** Stored energy in FE (for displays). */
    public long storedFe() {
        return ChargeMath.feAvailable(this.energyBuffer().getRaw(), EnergyConversions.neroToForgeRatio());
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        if (++this.phase < PERIOD) {
            return;
        }
        this.phase = 0;
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            // Scrap Eggs incubate on a pad with or without power.
            za.co.neroland.nerocompanion.incubation.Incubators.tick(server, pos, PERIOD);
        }
        double ratio = EnergyConversions.neroToForgeRatio();
        if (this.energyBuffer().getRaw() > 0) {
            // Docked companions (on the plate) get the charger hook whether or not they need energy.
            for (CompanionEntity companion : level.getEntitiesOfClass(CompanionEntity.class,
                    new AABB(pos).expandTowards(0.0D, 1.0D, 0.0D), CompanionEntity::isAlive)) {
                if (companion.blockPosition().equals(pos) || companion.blockPosition().equals(pos.above())) {
                    companion.chargerTick(PERIOD);
                }
            }
        }
        long available = ChargeMath.feAvailable(this.energyBuffer().getRaw(), ratio);
        boolean charged = false;
        if (available > 0) {
            double r = ChargeMath.PAD_NEARBY_RANGE;
            AABB box = new AABB(pos).inflate(r, 1.0D, r);
            List<CompanionEntity> near = level.getEntitiesOfClass(CompanionEntity.class, box,
                    companion -> companion.isAlive() && companion.energy().stored() < companion.energy().capacity());
            List<ChargeMath.Want> wants = new ArrayList<>(near.size());
            for (CompanionEntity companion : near) {
                boolean docked = companion.blockPosition().equals(pos) || companion.blockPosition().equals(pos.above());
                double distance = Math.sqrt(companion.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D));
                wants.add(new ChargeMath.Want(
                        (companion.energy().capacity() - companion.energy().stored() + PERIOD - 1) / PERIOD,
                        docked, distance));
            }
            long[] plan = ChargeMath.padPlan(available / PERIOD, wants);
            long spent = 0;
            for (int i = 0; i < plan.length; i++) {
                if (plan[i] > 0) {
                    long given = near.get(i).receiveCharge(plan[i] * PERIOD, plan[i]);
                    spent += given;
                }
            }
            if (spent > 0) {
                long cost = Math.min(this.energyBuffer().getRaw(), ChargeMath.neCost(spent, ratio));
                this.energyBuffer().setRaw((int) (this.energyBuffer().getRaw() - cost));
                charged = true;
            }
        }
        boolean powered = this.energyBuffer().getRaw() > 0;
        if (state.getValue(ChargingPadBlock.CHARGING) != charged || state.getValue(ChargingPadBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(ChargingPadBlock.CHARGING, charged)
                    .setValue(ChargingPadBlock.POWERED, powered), 3);
        }
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof net.minecraft.server.level.ServerLevel server) {
            za.co.neroland.nerocompanion.incubation.Incubators.incubatorRemoved(server, pos);
        }
        super.preRemoveSideEffects(pos, state);
    }
    //?}
}
