package za.co.neroland.nerocompanion.neoforge;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.platform.NeoForgeEnergyLookup;

import za.co.neroland.nerocompanion.registry.ModBlockEntities;

/**
 * NeoForge: exposes the Charging Pad and the Companion Bay on Core's shared {@code nerolandcore:energy} block
 * capability, gated per face by their side configuration (all faces accept by default).
 */
public final class NeoForgeCompanionCapabilities {

    private NeoForgeCompanionCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeCompanionCapabilities::onRegister);
    }

    private static void onRegister(RegisterCapabilitiesEvent event) {
        energy(event, ModBlockEntities.CHARGING_PAD.get());
        energy(event, ModBlockEntities.COMPANION_BAY.get());
    }

    private static <T extends AbstractMachineBlockEntity> void energy(RegisterCapabilitiesEvent event,
            BlockEntityType<T> type) {
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, type,
                (be, side) -> be.sideConfig() != null ? be.sideConfig().energyView(side) : be.getEnergy());
    }
}
