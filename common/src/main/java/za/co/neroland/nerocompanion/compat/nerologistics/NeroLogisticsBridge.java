package za.co.neroland.nerocompanion.compat.nerologistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerologistics.api.NeroLogisticsStorage;

/**
 * The <b>only</b> class that names a NeroLogistics type. Loaded only from {@link NeroLogisticsCompat} after
 * {@code isModLoaded("nerologistics")}; compiled {@code compileOnly} against NeroLogistics' stable public API
 * ({@code api.NeroLogisticsStorage}, 0.7.6 or later), never its internal packages.
 */
final class NeroLogisticsBridge {

    private NeroLogisticsBridge() {
    }

    /** Resolves the API now, so a missing or too-old NeroLogistics fails at init, not mid-deposit. */
    static void probe() {
        NeroLogisticsStorage.class.getName();
    }

    /**
     * An insert-only inlet at {@code pos}: an active drive bay takes items directly; otherwise, if an item network is
     * reachable from {@code pos} (a terminal, a conduit, a block next to one), the network spreads them over its
     * storage. Null when neither applies.
     */
    static Container inlet(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        if (NeroLogisticsStorage.isStorageNode(level, pos)) {
            return new Inlet(level, pos, true);
        }
        if (NeroLogisticsStorage.hasNetworkAt(level, pos)) {
            return new Inlet(level, pos, false);
        }
        return null;
    }

    /**
     * One empty slot that takes a stack only when the target can take all of it (simulated first), then inserts it. A
     * remainder from a race between the simulation and the insert is popped at the target, never voided.
     */
    static final class Inlet extends SimpleContainer {

        private final ServerLevel level;
        private final BlockPos pos;
        private final boolean driveBay;

        Inlet(ServerLevel level, BlockPos pos, boolean driveBay) {
            super(1);
            this.level = level;
            this.pos = pos;
            this.driveBay = driveBay;
        }

        private long insert(ItemStack stack, boolean simulate) {
            return this.driveBay ? NeroLogisticsStorage.insertAt(this.level, this.pos, stack, simulate)
                    : NeroLogisticsStorage.insertIntoNetwork(this.level, this.pos, stack, simulate);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !stack.isEmpty() && insert(stack, true) >= stack.getCount();
        }

        @Override
        public ItemStack getItem(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            long inserted = insert(stack, false);
            int rest = stack.getCount() - (int) Math.max(0L, Math.min(inserted, stack.getCount()));
            if (rest > 0) {
                Block.popResource(this.level, this.pos.above(), stack.copyWithCount(rest));
            }
        }
    }
}
