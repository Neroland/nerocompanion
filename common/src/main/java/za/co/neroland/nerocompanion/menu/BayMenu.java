package za.co.neroland.nerocompanion.menu;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.charge.BaySlots;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.registry.ModMenuTypes;

/**
 * The Companion Bay screen's menu: no slots, valid while the player stays within reach of the Bay. It syncs the
 * live numbers the gauges need through {@link ContainerData} (ints only; FE amounts as two 15-bit halves): the
 * buffer's stored and capacity FE, and the charge percent of each parked companion the viewer owns. Someone
 * else's parked companion always reads 0, as it does in the snapshot.
 */
public class BayMenu extends AbstractContainerMenu {

    /** Stored FE, low 15 bits. */
    public static final int DATA_STORED_LO = 0;
    /** Stored FE, high 15 bits. */
    public static final int DATA_STORED_HI = 1;
    /** Capacity FE, low 15 bits. */
    public static final int DATA_CAPACITY_LO = 2;
    /** Capacity FE, high 15 bits. */
    public static final int DATA_CAPACITY_HI = 3;
    /** First of the per-slot charge percents (viewer-owned slots only, else 0). */
    public static final int DATA_SLOT_PCT = 4;
    /** Set to 1 by the server once the values are live. */
    public static final int DATA_LIVE = DATA_SLOT_PCT + BaySlots.SIZE;
    public static final int DATA_COUNT = DATA_LIVE + 1;

    private static final int HALF_BITS = 15;
    private static final int HALF_MASK = (1 << HALF_BITS) - 1;
    private static final long MAX_SYNCED = (1L << (2 * HALF_BITS)) - 1L;

    @Nullable
    private final BlockPos bay;
    private final ContainerData data;

    public BayMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    @SuppressWarnings("this-escape")
    public BayMenu(int id, Inventory inventory, @Nullable BlockPos bay) {
        super(ModMenuTypes.BAY.get(), id);
        this.bay = bay;
        this.data = bay == null ? new SimpleContainerData(DATA_COUNT) : new ServerData(inventory.player, bay);
        this.addDataSlots(this.data);
    }

    public static MenuProvider provider(BlockPos bay) {
        return new SimpleMenuProvider((id, inventory, player) -> new BayMenu(id, inventory, bay),
                Component.translatable("gui.nerocompanion.bay.title"));
    }

    @Override
    public boolean stillValid(Player player) {
        return this.bay == null || (player.level().getBlockEntity(this.bay) instanceof CompanionBayBlockEntity
                && player.distanceToSqr(this.bay.getX() + 0.5D, this.bay.getY() + 0.5D, this.bay.getZ() + 0.5D)
                        <= CompanionBayBlockEntity.REACH * CompanionBayBlockEntity.REACH);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // --- synced gauge values (client reads) ----------------------------------------------------------

    /** Whether the server has sent live values yet (false for the first frames after opening). */
    public boolean live() {
        return this.data.get(DATA_LIVE) == 1;
    }

    /** The Bay's stored FE (clamped to 30 bits). */
    public long storedFe() {
        return join(this.data.get(DATA_STORED_LO), this.data.get(DATA_STORED_HI));
    }

    /** The Bay's capacity FE (clamped to 30 bits). */
    public long capacityFe() {
        return join(this.data.get(DATA_CAPACITY_LO), this.data.get(DATA_CAPACITY_HI));
    }

    /** Charge percent of parked slot {@code index} (0 for an empty or someone else's slot). */
    public int slotPercent(int index) {
        if (index < 0 || index >= BaySlots.SIZE) {
            return 0;
        }
        return Math.max(0, Math.min(100, this.data.get(DATA_SLOT_PCT + index)));
    }

    static int low(long value) {
        return (int) (Math.max(0L, Math.min(MAX_SYNCED, value)) & HALF_MASK);
    }

    static int high(long value) {
        return (int) ((Math.max(0L, Math.min(MAX_SYNCED, value)) >> HALF_BITS) & HALF_MASK);
    }

    static long join(int low, int high) {
        return ((long) (high & HALF_MASK) << HALF_BITS) | (low & HALF_MASK);
    }

    /** Server side: reads the Bay each sync; only the viewer's own parked companions report a percent. */
    private static final class ServerData implements ContainerData {

        private final Player viewer;
        private final BlockPos bay;

        ServerData(Player viewer, BlockPos bay) {
            this.viewer = viewer;
            this.bay = bay;
        }

        @Nullable
        private CompanionBayBlockEntity entity() {
            return this.viewer.level().getBlockEntity(this.bay) instanceof CompanionBayBlockEntity be ? be : null;
        }

        @Override
        public int get(int index) {
            CompanionBayBlockEntity be = this.entity();
            if (be == null) {
                return 0;
            }
            return switch (index) {
                case DATA_STORED_LO -> low(be.storedFe());
                case DATA_STORED_HI -> high(be.storedFe());
                case DATA_CAPACITY_LO -> low(be.capacityFe());
                case DATA_CAPACITY_HI -> high(be.capacityFe());
                case DATA_LIVE -> 1;
                default -> index >= DATA_SLOT_PCT && index < DATA_SLOT_PCT + BaySlots.SIZE
                        ? this.slotPercent(be, index - DATA_SLOT_PCT) : 0;
            };
        }

        private int slotPercent(CompanionBayBlockEntity be, int slot) {
            Optional<CompanionRecord> record = be.slots().get(slot);
            MinecraftServer server = this.viewer.level().getServer();
            if (record.isEmpty() || server == null || !record.get().ownerId().equals(this.viewer.getUUID())) {
                return 0;
            }
            return CompanionBayBlockEntity.percent(record.get().energyFe(),
                    CompanionBayBlockEntity.capacityOf(server, record.get()));
        }

        @Override
        public void set(int index, int value) {
            // Server values are read-only.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
