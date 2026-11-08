package za.co.neroland.nerocompanion.forge;

import java.util.EnumMap;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
*///?}

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.platform.ForgeEnergyLookup;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.charge.ChargingPadBlockEntity;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;

/**
 * Forge: attaches Core's shared {@code nerolandcore:energy} capability to the Charging Pad and the Companion Bay,
 * gated per face by their side configuration.
 */
public final class ForgeCompanionCapabilities {

    private static final Identifier ENERGY_CAP = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "energy");

    private ForgeCompanionCapabilities() {
    }

    public static void register() {
        //? if >=1.21.6 {
        AttachCapabilitiesEvent.BlockEntities.BUS.addListener(ForgeCompanionCapabilities::onAttach);
        //?} else {
        /*MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.block.entity.BlockEntity.class,
                ForgeCompanionCapabilities::onAttach);
        *///?}
    }

    //? if >=1.21.6 {
    private static void onAttach(AttachCapabilitiesEvent.BlockEntities event) {
    //?} else {
    /*private static void onAttach(AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity> event) {
    *///?}
        if (event.getObject() instanceof ChargingPadBlockEntity || event.getObject() instanceof CompanionBayBlockEntity) {
            Provider provider = new Provider((AbstractMachineBlockEntity) event.getObject());
            event.addCapability(ENERGY_CAP, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static final class Provider implements ICapabilityProvider {
        private final AbstractMachineBlockEntity machine;
        private final LazyOptional<NeroEnergyStorage> unsided;
        private final EnumMap<Direction, LazyOptional<NeroEnergyStorage>> sided = new EnumMap<>(Direction.class);

        Provider(AbstractMachineBlockEntity machine) {
            this.machine = machine;
            this.unsided = LazyOptional.of(machine::getEnergy);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap != ForgeEnergyLookup.ENERGY) {
                return LazyOptional.empty();
            }
            if (side == null || this.machine.sideConfig() == null) {
                return this.unsided.cast();
            }
            return this.sided.computeIfAbsent(side, d -> {
                NeroEnergyStorage view = this.machine.sideConfig().energyView(d);
                return view == null ? LazyOptional.empty() : LazyOptional.of(() -> view);
            }).cast();
        }

        void invalidate() {
            this.unsided.invalidate();
            this.sided.values().forEach(LazyOptional::invalidate);
            this.sided.clear();
        }
    }
}
