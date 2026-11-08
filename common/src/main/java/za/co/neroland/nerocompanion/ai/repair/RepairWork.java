package za.co.neroland.nerocompanion.ai.repair;

import java.util.EnumSet;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/** Registers the Repair Bot's work goal. Called once from the common content switchboard. */
public final class RepairWork {

    /** Work goal id for {@code companion_types/repair_bot.json}. */
    public static final Identifier REPAIR_GEAR = Identifier.fromNamespaceAndPath("nerocompanion", "repair_gear");

    private RepairWork() {
    }

    public static void register() {
        if (WorkGoals.get(REPAIR_GEAR).isEmpty()) {
            WorkGoals.register(REPAIR_GEAR, WorkGoalFactory.of(EnumSet.of(CompanionMode.REPAIR), RepairGoal::new));
        }
    }
}
