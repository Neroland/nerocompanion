package za.co.neroland.nerocompanion.menu;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.registry.ModMenuTypes;

/**
 * The Companion screen's menu (also used by the Skin Studio). It has no slots: chips travel through
 * {@code ChipEdit} and every other control sends a payload the server re-validates; the screen draws from the
 * owner-only {@code CompanionSnapshot}. The server side remembers which companion it shows.
 */
public class CompanionMenu extends AbstractContainerMenu {

    @Nullable
    private final UUID companionId;

    /** Client constructor (the screen learns the companion from the snapshot). */
    public CompanionMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public CompanionMenu(int id, Inventory inventory, @Nullable UUID companionId) {
        super(ModMenuTypes.COMPANION.get(), id);
        this.companionId = companionId;
    }

    /** Server: the companion this menu shows (null on the client). */
    @Nullable
    public UUID companionId() {
        return this.companionId;
    }

    public static MenuProvider provider(UUID companionId) {
        return new SimpleMenuProvider((id, inventory, player) -> new CompanionMenu(id, inventory, companionId),
                Component.translatable("gui.nerocompanion.companion.menu"));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
