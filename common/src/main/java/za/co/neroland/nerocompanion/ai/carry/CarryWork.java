package za.co.neroland.nerocompanion.ai.carry;

import java.util.EnumSet;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.carry.ContainerBinding;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.item.RemoteBlockActions;

/** WP-B carry registration: the {@code carry_haul} work goal and the Remote container binding. */
public final class CarryWork {

    public static final Identifier CARRY_HAUL = Identifier.fromNamespaceAndPath("nerocompanion", "carry_haul");

    private static boolean registered;

    private CarryWork() {
    }

    /** Common init (once). Idempotent so unit tests may call it too. */
    public static synchronized void register() {
        if (registered || WorkGoals.get(CARRY_HAUL).isPresent()) {
            registered = true;
            return;
        }
        registered = true;
        WorkGoals.register(CARRY_HAUL, WorkGoalFactory.of(EnumSet.of(CompanionMode.CARRY), CarryGoal::new));
        RemoteBlockActions.register(ContainerBinding::onSneakUse);
    }
}
