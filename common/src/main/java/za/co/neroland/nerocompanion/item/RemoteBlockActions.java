package za.co.neroland.nerocompanion.item;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * What a sneak-use of the Remote on a block does (bind a carry container, mark a mining corner...). Handlers are
 * registered during common init and asked in registration order on the server; the first non-PASS result wins.
 * A sneak-use on a block never opens the Remote's hold radial, whether or not a handler acts. Each handler checks
 * its own authority ({@code OwnerAuthority}) and picks its own companion; the Remote carries no selection.
 */
public final class RemoteBlockActions {

    /** One sneak-use handler. */
    @FunctionalInterface
    public interface Handler {
        InteractionResult onSneakUse(ServerPlayer player, ServerLevel level, BlockPos pos, Direction face,
                ItemStack remote);
    }

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

    private RemoteBlockActions() {
    }

    public static void register(Handler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        HANDLERS.add(handler);
    }

    /** Server: asks each handler in turn; PASS when none acts. */
    public static InteractionResult dispatch(ServerPlayer player, ServerLevel level, BlockPos pos, Direction face,
            ItemStack remote) {
        for (Handler handler : HANDLERS) {
            InteractionResult result = handler.onSneakUse(player, level, pos, face, remote);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }
}
