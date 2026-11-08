package za.co.neroland.nerocompanion.compat.nerologistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerologistics.storage.StorageNode;

/**
 * The <b>only</b> class that names a NeroLogistics type. Loaded only from {@link NeroLogisticsCompat} after
 * {@code isModLoaded("nerologistics")}; compiled against the never-packaged stub of {@code StorageNode} (tag
 * {@code v0.7.0-beta.1}).
 */
final class NeroLogisticsBridge {

    private NeroLogisticsBridge() {
    }

    /** Resolves the interface now, so a missing or changed NeroLogistics fails at init, not mid-deposit. */
    static void probe() {
        StorageNode.class.getName();
    }

    static Container inlet(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        if (!(blockEntity instanceof StorageNode node) || !node.storageNodeActive()) {
            return null;
        }
        return new DriveBayInlet(level, pos, node);
    }

    /**
     * One empty slot that takes a stack only when the bay can take all of it (simulated first), then inserts it. A
     * remainder from a race between the simulation and the insert is popped at the bay, never voided.
     */
    static final class DriveBayInlet extends SimpleContainer {

        private final ServerLevel level;
        private final BlockPos pos;
        private final StorageNode node;

        DriveBayInlet(ServerLevel level, BlockPos pos, StorageNode node) {
            super(1);
            this.level = level;
            this.pos = pos;
            this.node = node;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !stack.isEmpty() && this.node.storageNodeActive()
                    && this.node.insertItem(stack, stack.getCount(), true) >= stack.getCount();
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
            long inserted = this.node.insertItem(stack, stack.getCount(), false);
            int rest = stack.getCount() - (int) Math.max(0L, Math.min(inserted, stack.getCount()));
            if (rest > 0) {
                Block.popResource(this.level, this.pos.above(), stack.copyWithCount(rest));
            }
        }
    }
}
