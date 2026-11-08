package za.co.neroland.nerocompanion.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.personality.PersonalityHooks;

/**
 * The mode state machine: owns the companion's goal set. On a change of effective mode it
 * removes the goals the new mode does not use (vanilla stops a running goal on removal) and adds the ones it
 * does, at the table's priorities. Goal instances are created once per slot and reused. Server only.
 *
 * <p>Work goals are not a table slot: the type's {@code work_goals} ids are looked up in {@link WorkGoals}, and each
 * factory's goal is installed at priority 4 ({@link GoalSlot#WORK}) while the effective mode is one of the
 * factory's {@link WorkGoalFactory#modes()} (any awake mode, not only the table's work modes), in the goal or
 * target selector as the factory says. Trait idle goals come from {@link PersonalityHooks#idleGoals} and are
 * installed at priority 7 ({@link GoalSlot#TRAIT_IDLE}) in the modes the table gives that slot.
 */
public final class ModeController {

    private final CompanionEntity companion;
    private final GoalSelector selector;
    private final GoalSelector targetSelector;
    private final Map<GoalSlot, Goal> goals = new EnumMap<>(GoalSlot.class);
    private final EnumSet<GoalSlot> installed = EnumSet.noneOf(GoalSlot.class);
    /** Work goals created so far, by id (created once; dropped on {@link #reset()}). */
    private final Map<Identifier, Goal> workGoals = new HashMap<>();
    /** Installed work goals, by id, with the selector each went into. */
    private final Map<Identifier, GoalSelector> installedWork = new LinkedHashMap<>();
    /** Trait idle goals from the personality hooks (created once; dropped on {@link #reset()}). */
    @Nullable
    private List<Goal> traitIdle;
    private boolean traitIdleInstalled;
    @Nullable
    private CompanionMode applied;
    /** The death-salvage gather goal (companions that salvage only; created once, dropped on reset). */
    @Nullable
    private Goal salvage;
    private boolean salvageInstalled;
    /** The installed set is the salvage-only one (gathering while the owner is elsewhere). */
    private boolean appliedSalvageOnly;

    public ModeController(CompanionEntity companion, GoalSelector selector, GoalSelector targetSelector) {
        this.companion = companion;
        this.selector = selector;
        this.targetSelector = targetSelector;
    }

    /** The effective mode whose goal set is installed, or null before the first apply. */
    @Nullable
    public CompanionMode applied() {
        return this.applied;
    }

    /** Installs the goal set for {@code mode} if it is not already the installed one. */
    public void apply(CompanionMode mode) {
        this.apply(mode, false);
    }

    /**
     * Installs the goal set for {@code mode}; with {@code salvageOnly} (gathering the owner's death drops while the
     * owner is in another dimension or out of range) only floating and the salvage goal - no work, follow or idle goal.
     */
    public void apply(CompanionMode mode, boolean salvageOnly) {
        if (mode == this.applied && salvageOnly == this.appliedSalvageOnly) {
            return;
        }
        CompanionMode previous = this.applied;
        this.applied = mode;
        this.appliedSalvageOnly = salvageOnly;
        Set<GoalSlot> wanted = EnumSet.noneOf(GoalSlot.class);
        wanted.addAll(ModeGoalTable.slotsFor(mode, this.companion.locomotion() == Locomotion.GROUND,
                this.companion.isSmallChassis()));
        if (salvageOnly) {
            wanted.retainAll(EnumSet.of(GoalSlot.FLOAT));
        }
        for (GoalSlot slot : EnumSet.copyOf(this.installed)) {
            if (!wanted.contains(slot)) {
                Goal goal = this.goals.get(slot);
                if (goal != null) {
                    this.selector.removeGoal(goal);
                }
                this.installed.remove(slot);
            }
        }
        for (GoalSlot slot : wanted) {
            if (this.installed.contains(slot)) {
                continue;
            }
            Goal goal = this.goals.computeIfAbsent(slot, this::create);
            if (goal != null) {
                this.selector.addGoal(slot.priority(), goal);
                this.installed.add(slot);
            }
        }
        this.applyTraitIdle(wanted.contains(GoalSlot.TRAIT_IDLE));
        this.applyWork(salvageOnly ? CompanionMode.SLEEP : mode);
        this.applySalvage(mode != CompanionMode.SLEEP
                && za.co.neroland.nerocompanion.salvage.Salvage.gathers(this.companion));
        if (mode == CompanionMode.SLEEP || previous == CompanionMode.SLEEP || previous == null) {
            this.companion.getNavigation().stop();
        }
        if (mode != CompanionMode.FOLLOW) {
            this.companion.stopPerch();
        }
    }

    /** Drops every installed goal so the next {@link #apply} rebuilds (the type changed). */
    public void reset() {
        for (GoalSlot slot : this.installed) {
            Goal goal = this.goals.get(slot);
            if (goal != null) {
                this.selector.removeGoal(goal);
            }
        }
        this.installed.clear();
        this.goals.clear();
        this.applyTraitIdle(false);
        this.traitIdle = null;
        this.applySalvage(false);
        this.salvage = null;
        for (Map.Entry<Identifier, GoalSelector> entry : this.installedWork.entrySet()) {
            Goal goal = this.workGoals.get(entry.getKey());
            if (goal != null) {
                entry.getValue().removeGoal(goal);
            }
        }
        this.installedWork.clear();
        this.workGoals.clear();
        this.applied = null;
        this.appliedSalvageOnly = false;
    }

    /** Ids of the work goals currently installed (for debug output and tests of callers). */
    public List<Identifier> installedWorkGoals() {
        return List.copyOf(this.installedWork.keySet());
    }

    private void applyWork(CompanionMode mode) {
        CompanionType type = this.companion.typeDef();
        List<Identifier> wanted = type == null ? List.of() : WorkGoals.activeFor(WorkGoals.withChipGoals(
                type.workGoals(), this.companion.activeChips().stream().map(chip -> chip.chip()).toList()), mode);
        for (Identifier id : new ArrayList<>(this.installedWork.keySet())) {
            if (!wanted.contains(id)) {
                Goal goal = this.workGoals.get(id);
                if (goal != null) {
                    this.installedWork.get(id).removeGoal(goal);
                }
                this.installedWork.remove(id);
            }
        }
        if (type == null) {
            return;
        }
        for (Identifier id : wanted) {
            if (this.installedWork.containsKey(id)) {
                continue;
            }
            WorkGoalFactory factory = WorkGoals.get(id).orElse(null);
            if (factory == null) {
                continue;
            }
            Goal goal = this.workGoals.computeIfAbsent(id, key -> factory.create(this.companion, type));
            GoalSelector into = factory.target() ? this.targetSelector : this.selector;
            into.addGoal(GoalSlot.WORK.priority(), goal);
            this.installedWork.put(id, into);
        }
    }

    /** The salvage goal at priority 3 (below return-home, above work), in every awake mode, for salvagers only. */
    private void applySalvage(boolean wanted) {
        if (wanted == this.salvageInstalled) {
            return;
        }
        if (wanted) {
            if (this.salvage == null) {
                this.salvage = new za.co.neroland.nerocompanion.salvage.SalvageGatherGoal(this.companion);
            }
            this.selector.addGoal(GoalSlot.PERCH.priority(), this.salvage);
        } else if (this.salvage != null) {
            this.selector.removeGoal(this.salvage);
        }
        this.salvageInstalled = wanted;
    }

    private void applyTraitIdle(boolean wanted) {
        if (wanted == this.traitIdleInstalled) {
            return;
        }
        if (wanted) {
            if (this.traitIdle == null) {
                this.traitIdle = List.copyOf(PersonalityHooks.get().idleGoals(this.companion));
            }
            for (Goal goal : this.traitIdle) {
                this.selector.addGoal(GoalSlot.TRAIT_IDLE.priority(), goal);
            }
        } else if (this.traitIdle != null) {
            for (Goal goal : this.traitIdle) {
                this.selector.removeGoal(goal);
            }
        }
        this.traitIdleInstalled = wanted;
    }

    @Nullable
    private Goal create(GoalSlot slot) {
        return switch (slot) {
            case FLOAT -> new FloatGoal(this.companion);
            case SLEEP -> new SleepGoal(this.companion);
            case RETURN_HOME -> new ReturnHomeGoal(this.companion);
            case PERCH -> new PerchGoal(this.companion);
            case FOLLOW -> new FollowOwnerGoal(this.companion);
            case STAY -> new StayGoal(this.companion);
            case WANDER -> new IdleWanderGoal(this.companion);
            case LOOK -> new LookAtOwnerGoal(this.companion);
            case WORK, TRAIT_IDLE -> null;
        };
    }
}
