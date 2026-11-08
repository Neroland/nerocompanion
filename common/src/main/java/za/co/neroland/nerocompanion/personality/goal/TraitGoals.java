package za.co.neroland.nerocompanion.personality.goal;

import java.util.List;

import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.personality.Trait;

/** The idle goals a trait installs (priority 7, FOLLOW and STAY): its own habit goal plus bond-1 look-around. */
public final class TraitGoals {

    private TraitGoals() {
    }

    public static List<Goal> create(CompanionEntity companion, Trait trait) {
        Goal own = switch (trait) {
            case CURIOUS -> new CuriousGoal(companion);
            case LOYAL -> new LoyalGoal(companion);
            case GRUMPY -> new GrumpyGoal(companion);
            case SLEEPY -> new SleepyGoal(companion);
            case SHOWOFF -> new ShowoffGoal(companion);
            case SHY -> new ShyGoal(companion);
        };
        return List.of(own, new LookAroundGoal(companion));
    }
}
