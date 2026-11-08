package za.co.neroland.nerocompanion.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.bond.BondEvents;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * The real events behind the {@link CompanionMilestones} record gates. Every entry point takes the server and the
 * <b>stored owner UUID</b> (never a nearby player), and opens the gates through Core's offline-capable
 * {@code ProgressionGates.open(server, uuid, gate)}, so a milestone a companion earns while its owner is away is still
 * credited. Opening an open gate is a no-op in Core, so every call is idempotent.
 *
 * <p>The decisions ({@link #onDeploy}, {@link #forWork}, {@link #bondMilestone}) are pure for the tests; the gate
 * writes go through a {@link Sink} the tests can replace.
 *
 * <p>No {@code ThresholdEvents} channel is published: every one of these is about one owner, and a crossing must
 * never name a person (Core's contract), so the quest pack reads the player-scope record gates with
 * {@code gate_open} instead. Privacy (POPIA/GDPR): the owner UUID goes only into Core's gate store, which Core
 * exports and erases; nothing is logged.
 */
public final class CompanionProgress {

    /** The kinds of work a quest counts. */
    public enum Work {
        MINE, CARRY, REPAIR, GUARD
    }

    /** Where gate opens go. */
    @FunctionalInterface
    public interface Sink {
        void open(@Nullable MinecraftServer server, UUID owner, Identifier gate);
    }

    private static final Sink CORE = (server, owner, gate) -> {
        if (server != null) {
            ProgressionGates.open(server, owner, gate);
        }
    };
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Sink sink = CORE;
    private static boolean listening;

    private CompanionProgress() {
    }

    /** Common init (quest hooks step): listen for bond level-ups. Idempotent. */
    public static synchronized void init() {
        if (listening) {
            return;
        }
        listening = true;
        BondEvents.onLevelUp(event -> {
            CompanionEntity companion = event.companion();
            if (companion.level() instanceof ServerLevel level && companion.ownerId() != null) {
                bondLevelUp(level.getServer(), companion.ownerId(), event.from(), event.to());
            }
        });
    }

    /** Tests: route gate opens elsewhere ({@code null} restores Core). */
    static void useSink(@Nullable Sink replacement) {
        sink = replacement == null ? CORE : replacement;
    }

    // --- the rules ------------------------------------------------------------------------------------

    /** The gates a deploy opens: always {@code first_deploy}; {@code full_squad} once the LIVE count reaches the cap. */
    public static List<Identifier> onDeploy(int liveAfter, int cap) {
        List<Identifier> out = new ArrayList<>(2);
        out.add(CompanionMilestones.FIRST_DEPLOY);
        if (cap > 0 && liveAfter >= cap) {
            out.add(CompanionMilestones.FULL_SQUAD);
        }
        return out;
    }

    /** The gate a unit of work opens. */
    public static Identifier forWork(Work work) {
        return switch (work) {
            case MINE -> CompanionMilestones.FIRST_MINE;
            case CARRY -> CompanionMilestones.FIRST_CARRY;
            case REPAIR -> CompanionMilestones.FIRST_REPAIR;
            case GUARD -> CompanionMilestones.FIRST_GUARD;
        };
    }

    /** Whether a level-up from {@code from} to {@code to} reaches bond level 3. */
    public static boolean bondMilestone(int from, int to) {
        return to >= 3 && to > from;
    }

    // --- the events -----------------------------------------------------------------------------------

    /** A companion of {@code owner} went LIVE; {@code liveAfter} counts it. */
    public static void deployed(@Nullable MinecraftServer server, @Nullable UUID owner, int liveAfter, int cap) {
        for (Identifier gate : onDeploy(liveAfter, cap)) {
            open(server, owner, gate);
        }
    }

    /** A chip went into one of the owner's companions. */
    public static void chipInstalled(@Nullable MinecraftServer server, @Nullable UUID owner) {
        open(server, owner, CompanionMilestones.FIRST_CHIP);
    }

    /** A charger put energy into one of the owner's companions. */
    public static void charged(@Nullable MinecraftServer server, @Nullable UUID owner) {
        open(server, owner, CompanionMilestones.FIRST_CHARGE);
    }

    /** One of the owner's companions finished a unit of {@code work}. */
    public static void workDone(@Nullable MinecraftServer server, @Nullable UUID owner, Work work) {
        open(server, owner, forWork(work));
    }

    /** A bond level-up of one of the owner's companions. */
    public static void bondLevelUp(@Nullable MinecraftServer server, @Nullable UUID owner, int from, int to) {
        if (bondMilestone(from, to)) {
            open(server, owner, CompanionMilestones.BOND_THREE);
        }
    }

    /** A Scrap Egg of the owner hatched: also a pet adoption. */
    public static void hatched(@Nullable MinecraftServer server, @Nullable UUID owner) {
        open(server, owner, CompanionMilestones.FIRST_HATCH);
        open(server, owner, CompanionMilestones.PET_ADOPTED);
    }

    /** A NeroCreatures pet was tamed by the owner. */
    public static void petAdopted(@Nullable MinecraftServer server, @Nullable UUID owner) {
        open(server, owner, CompanionMilestones.PET_ADOPTED);
    }

    /** The owner reclaimed one of their LOST companions. */
    public static void recovered(@Nullable MinecraftServer server, @Nullable UUID owner) {
        open(server, owner, CompanionMilestones.FIRST_RECOVERY);
    }

    private static void open(@Nullable MinecraftServer server, @Nullable UUID owner, Identifier gate) {
        if (owner == null) {
            return;
        }
        try {
            sink.open(server, owner, gate);
        } catch (RuntimeException failure) {
            // A milestone must never break the gameplay that earned it. No player data in the log.
            if (WARNED.compareAndSet(false, true)) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a milestone gate could not be opened ({})",
                        failure.getClass().getSimpleName());
            }
        }
    }
}
