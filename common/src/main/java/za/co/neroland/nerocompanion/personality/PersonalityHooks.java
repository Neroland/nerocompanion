package za.co.neroland.nerocompanion.personality;

import java.util.List;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Where personality and bond plug into the companion. The base entity and the mode controller call these; one
 * implementation is installed with {@link #set} during common init, and until then (or if none is) every call is a
 * no-op. All calls are server side.
 */
public interface PersonalityHooks {

    PersonalityHooks NONE = new PersonalityHooks() { };

    /**
     * Once per scheduler evaluation (every 20 ticks, staggered per companion), after the throttle, drain and goal
     * swap - awake or asleep, never while the companion is inert (unreadable save).
     */
    default void onTick(CompanionEntity companion) {
    }

    /**
     * The owner used an empty main hand on the companion without sneaking, and no per-type
     * {@code onInteractOwner} handled it. Return a non-PASS result to consume the click.
     */
    default InteractionResult onPetted(CompanionEntity companion, Player owner) {
        return InteractionResult.PASS;
    }

    /** A work package finished a unit of work ({@link CompanionEntity#jobDone()}). */
    default void onJobDone(CompanionEntity companion) {
    }

    /**
     * Trait idle goals for {@code companion}, created once per goal-set build and installed at priority 7 in the
     * modes the goal table gives that slot (FOLLOW, STAY). An empty list installs nothing.
     */
    default List<Goal> idleGoals(CompanionEntity companion) {
        return List.of();
    }

    /** The installed hooks ({@link #NONE} until {@link #set}). */
    static PersonalityHooks get() {
        return Holder.current;
    }

    /** Installs the implementation (common init). {@code null} restores {@link #NONE}. */
    static void set(PersonalityHooks hooks) {
        Holder.current = hooks == null ? NONE : hooks;
    }

    /** Storage for {@link #get()}; not API. */
    final class Holder {
        private static volatile PersonalityHooks current = NONE;

        private Holder() {
        }
    }
}
