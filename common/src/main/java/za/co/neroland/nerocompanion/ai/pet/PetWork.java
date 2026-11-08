package za.co.neroland.nerocompanion.ai.pet;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.goal.Goal;


import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.job.JobCodecs;

/**
 * The pet package (WP-D): the Scrapling's work goals, its fetch job codec, and the erasure of the pet data it keeps
 * (incubating Scrap Eggs, the NeroCreatures pet list). Called once from the common content switchboard.
 *
 * <p>Comfort, never combat: every pet goal is a plain goal-selector goal ({@link Spec#target()} is false for all of
 * them), none deals damage, none touches stats.
 */
public final class PetWork {

    public static final Identifier FETCH = id("pet_fetch");
    public static final Identifier PLAY = id("pet_play");
    public static final Identifier NAP = id("pet_nap");

    /** A pet work goal: its id, the modes it runs in, whether it is a target goal (never, for pets). */
    public record Spec(Identifier id, Set<CompanionMode> modes, boolean target) {
    }

    /** Every pet goal, as data (the unit tests read this without creating goals). */
    public static final List<Spec> SPECS = List.of(
            new Spec(FETCH, EnumSet.of(CompanionMode.FOLLOW), false),
            new Spec(NAP, EnumSet.of(CompanionMode.FOLLOW, CompanionMode.STAY), false),
            new Spec(PLAY, EnumSet.of(CompanionMode.FOLLOW, CompanionMode.STAY), false));

    private static boolean done;

    private PetWork() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion", path);
    }

    public static synchronized void register() {
        if (done) {
            return;
        }
        done = true;
        add(FETCH, PetFetchGoal::new);
        add(NAP, PetNapGoal::new);
        add(PLAY, PetPlayGoal::new);
        if (!JobCodecs.isRegistered(FetchJob.ID)) {
            JobCodecs.register(FetchJob.ID, FetchJob.CODEC);
        }
        // Personal data this package keeps (who placed each incubating egg, which NeroCreatures pets a player owns) is
        // erased through privacy.CompanionErasure with every other player-keyed store.
    }

    private static void add(Identifier id, BiFunction<CompanionEntity, CompanionType, Goal> create) {
        Spec spec = SPECS.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
        if (WorkGoals.get(id).isEmpty()) {
            WorkGoals.register(id, WorkGoalFactory.of(spec.modes(), spec.target(), create));
        }
    }
}
