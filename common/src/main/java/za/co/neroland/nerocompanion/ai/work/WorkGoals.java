package za.co.neroland.nerocompanion.ai.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * The work-goal registry: id to {@link WorkGoalFactory}. Code registers factories during common init (see
 * {@code registry/ModCompanionContent}); type files name them in {@code work_goals}, and
 * {@link #known()} is what the type parser accepts there. A type file naming an id nobody registered keeps
 * loading, with a validation issue for that entry.
 */
public final class WorkGoals {

    private static final Map<Identifier, WorkGoalFactory> FACTORIES = new ConcurrentHashMap<>();
    /** Chip id to the work goals that chip adds to any chassis it is installed in (e.g. guard protocol). */
    private static final Map<Identifier, List<Identifier>> CHIP_GOALS = new ConcurrentHashMap<>();

    private WorkGoals() {
    }

    /**
     * Registers {@code factory} under {@code id}.
     *
     * @throws IllegalStateException if {@code id} is already registered (two packages claiming one id is a bug)
     */
    public static void register(Identifier id, WorkGoalFactory factory) {
        if (id == null || factory == null) {
            throw new IllegalArgumentException("work goal id and factory are required");
        }
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalStateException("work goal " + id + " is already registered");
        }
    }

    /** The factory for {@code id}, if registered. */
    public static Optional<WorkGoalFactory> get(Identifier id) {
        return Optional.ofNullable(FACTORIES.get(id));
    }

    /**
     * A capability chip that unlocks a mode also brings that mode's work goals: with {@code chip} installed, a
     * companion runs {@code goals} in addition to its type's {@code work_goals}.
     */
    public static void grantByChip(Identifier chip, List<Identifier> goals) {
        CHIP_GOALS.merge(chip, List.copyOf(goals), (old, add) -> {
            List<Identifier> all = new ArrayList<>(old);
            add.stream().filter(goal -> !all.contains(goal)).forEach(all::add);
            return List.copyOf(all);
        });
    }

    /** The type's work goals followed by those granted by {@code chips}, without duplicates. */
    public static List<Identifier> withChipGoals(List<Identifier> workGoals, java.util.Collection<Identifier> chips) {
        List<Identifier> all = new ArrayList<>(workGoals);
        for (Identifier chip : chips) {
            for (Identifier goal : CHIP_GOALS.getOrDefault(chip, List.of())) {
                if (!all.contains(goal)) {
                    all.add(goal);
                }
            }
        }
        return all;
    }

    /** Every registered id: the parser's set of known work goals. */
    public static Set<Identifier> known() {
        return Set.copyOf(FACTORIES.keySet());
    }

    /**
     * Which of a type's {@code workGoals} run in {@code mode}, in the type's order (pure; the mode controller's
     * selection). Unregistered ids and SLEEP select nothing.
     */
    public static List<Identifier> activeFor(List<Identifier> workGoals, CompanionMode mode) {
        List<Identifier> active = new ArrayList<>();
        if (mode == CompanionMode.SLEEP) {
            return active;
        }
        for (Identifier id : workGoals) {
            WorkGoalFactory factory = FACTORIES.get(id);
            if (factory != null && factory.modes().contains(mode) && !active.contains(id)) {
                active.add(id);
            }
        }
        return active;
    }
}
