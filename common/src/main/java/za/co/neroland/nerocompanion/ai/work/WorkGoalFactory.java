package za.co.neroland.nerocompanion.ai.work;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;

import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.CompanionType;

/**
 * Makes one work goal for one companion. Registered under an id with {@link WorkGoals#register}; a companion type
 * lists the ids it runs in its JSON {@code work_goals}. The mode controller creates the goal once per companion
 * (on first need, again after a type reload) and installs it at priority 4 while the companion's effective mode is
 * one of {@link #modes()}; it is removed (and so stopped) on any other mode, and never runs asleep.
 */
public interface WorkGoalFactory {

    /** The modes this goal runs in. {@link CompanionMode#SLEEP} is ignored. */
    Set<CompanionMode> modes();

    /** A new goal instance for {@code companion} of chassis {@code type}. Server only; never returns null. */
    Goal create(CompanionEntity companion, CompanionType type);

    /**
     * True for a target goal ({@code TargetGoal}): installed in the companion's target selector instead of its goal
     * selector, still at priority 4.
     */
    default boolean target() {
        return false;
    }

    /** A goal-selector factory from a mode set and a constructor reference. */
    static WorkGoalFactory of(Set<CompanionMode> modes, BiFunction<CompanionEntity, CompanionType, Goal> create) {
        return of(modes, false, create);
    }

    /** A factory from a mode set, the selector choice and a constructor reference. */
    static WorkGoalFactory of(Set<CompanionMode> modes, boolean target,
            BiFunction<CompanionEntity, CompanionType, Goal> create) {
        EnumSet<CompanionMode> copy = EnumSet.noneOf(CompanionMode.class);
        copy.addAll(modes);
        copy.remove(CompanionMode.SLEEP);
        Set<CompanionMode> frozen = Set.copyOf(copy);
        return new WorkGoalFactory() {
            @Override
            public Set<CompanionMode> modes() {
                return frozen;
            }

            @Override
            public Goal create(CompanionEntity companion, CompanionType type) {
                return create.apply(companion, type);
            }

            @Override
            public boolean target() {
                return target;
            }
        };
    }
}
