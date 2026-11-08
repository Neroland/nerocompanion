package za.co.neroland.nerologistics.storage;

import java.util.function.BiConsumer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * COMPILE-TIME STUB, never packaged. Signature copy of NeroLogistics' {@code storage/StorageNode} at tag
 * {@code v0.7.0-beta.1} (implemented by {@code DriveBayBlockEntity}). Only NeroCompanion's
 * {@code compat/nerologistics/NeroLogisticsBridge} names it, and only after NeroLogistics is known to be loaded, so
 * at runtime the real interface is always the one resolved.
 */
public interface StorageNode {

    boolean storageNodeActive();

    int storagePriority();

    long storageVersion();

    long insertItem(ItemStack prototype, long amount, boolean simulate);

    long extractItem(ItemStack prototype, long amount, boolean simulate);

    void collectItems(BiConsumer<ItemStack, Long> out);

    long insertFluid(Fluid fluid, long amount, boolean simulate);

    long extractFluid(Fluid fluid, long amount, boolean simulate);

    void collectFluids(BiConsumer<Fluid, Long> out);
}
