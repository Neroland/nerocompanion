package za.co.neroland.nerocompanion.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.registry.ModMenuTypes;

/** The Companion Bay screen's menu: no slots, valid while the player stays within reach of the Bay. */
public class BayMenu extends AbstractContainerMenu {

    @Nullable
    private final BlockPos bay;

    public BayMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public BayMenu(int id, Inventory inventory, @Nullable BlockPos bay) {
        super(ModMenuTypes.BAY.get(), id);
        this.bay = bay;
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
}
