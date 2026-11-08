package za.co.neroland.nerocompanion.ai.combat;

import java.util.EnumSet;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * Registers the Combat Bot's two work goals: the target goal (target selector) and the melee goal. Both run in
 * GUARD (anchored on the owner) and STAY (anchored on the stay point: the bot guards its post).
 */
public final class CombatWork {

    public static final Identifier GUARD_TARGET = Identifier.fromNamespaceAndPath("nerocompanion", "guard_target");
    public static final Identifier GUARD_MELEE = Identifier.fromNamespaceAndPath("nerocompanion", "guard_melee");

    private CombatWork() {
    }

    public static void register() {
        EnumSet<CompanionMode> modes = EnumSet.of(CompanionMode.GUARD, CompanionMode.STAY);
        if (WorkGoals.get(GUARD_TARGET).isEmpty()) {
            WorkGoals.register(GUARD_TARGET, WorkGoalFactory.of(modes, true, GuardTargetGoal::new));
        }
        if (WorkGoals.get(GUARD_MELEE).isEmpty()) {
            WorkGoals.register(GUARD_MELEE, WorkGoalFactory.of(modes, GuardMeleeGoal::new));
        }
    }
}
