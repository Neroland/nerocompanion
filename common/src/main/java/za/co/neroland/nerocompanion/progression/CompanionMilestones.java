package za.co.neroland.nerocompanion.progression;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * The personal milestones NeroCompanion records as <b>player-scope Core gates</b>
 * ({@code data/nerocompanion/neroland_gates/*.json}). Each {@code record...} method is the one place
 * companion code calls when the matching thing happens; a quest pack then reads the gate with a
 * {@code gate_open} objective. Nothing here is a lock: the gates have no {@code requires} and are never
 * checked to allow or deny anything.
 *
 * <p>Where to call each one once companions exist:
 *
 * <ul>
 *   <li>{@link #recordFirstDeploy}: a companion is bound to its owner on first deploy from its item.</li>
 *   <li>{@link #recordFirstChip}: the owner inserts an upgrade chip into one of their companions.</li>
 *   <li>{@link #recordPetAdopted}: a pet alien is adopted / bound.</li>
 *   <li>{@link #recordFullSquad}: the owner's active companion count reaches the configured cap.</li>
 * </ul>
 *
 * <p>Opening an already-open gate is a no-op in Core, so every call is idempotent and may be made on every
 * occurrence. The overloads taking a {@link MinecraftServer} and a UUID cover an owner who is offline when
 * the milestone lands (a companion acting on its own); they need Core 1.15.0.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the owner UUID goes only into {@link ProgressionGates#open}, whose store
 * Core documents and erases; nothing is logged or broadcast about who reached a milestone. No channels
 * (threshold crossings) are published by this mod: a companion count is about one owner, so there is no
 * world milestone that would not hint at a specific player.
 */
public final class CompanionMilestones {

    /** A companion was bound to its owner on first deploy from its item. */
    public static final Identifier FIRST_DEPLOY = gate("first_deploy");
    /** The owner inserted an upgrade chip into one of their companions. */
    public static final Identifier FIRST_CHIP = gate("first_chip");
    /** A pet alien was adopted / bound. */
    public static final Identifier PET_ADOPTED = gate("pet_adopted");
    /** The owner's active companion count reached the configured cap. */
    public static final Identifier FULL_SQUAD = gate("full_squad");

    private CompanionMilestones() {
    }

    private static Identifier gate(String path) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, path);
    }

    /** Record {@link #FIRST_DEPLOY} for an online owner. */
    public static void recordFirstDeploy(ServerPlayer owner) {
        ProgressionGates.open(owner, FIRST_DEPLOY);
    }

    /** Record {@link #FIRST_DEPLOY} for an owner who may be offline. */
    public static void recordFirstDeploy(MinecraftServer server, UUID owner) {
        ProgressionGates.open(server, owner, FIRST_DEPLOY);
    }

    /** Record {@link #FIRST_CHIP} for an online owner. */
    public static void recordFirstChip(ServerPlayer owner) {
        ProgressionGates.open(owner, FIRST_CHIP);
    }

    /** Record {@link #FIRST_CHIP} for an owner who may be offline. */
    public static void recordFirstChip(MinecraftServer server, UUID owner) {
        ProgressionGates.open(server, owner, FIRST_CHIP);
    }

    /** Record {@link #PET_ADOPTED} for an online owner. */
    public static void recordPetAdopted(ServerPlayer owner) {
        ProgressionGates.open(owner, PET_ADOPTED);
    }

    /** Record {@link #PET_ADOPTED} for an owner who may be offline. */
    public static void recordPetAdopted(MinecraftServer server, UUID owner) {
        ProgressionGates.open(server, owner, PET_ADOPTED);
    }

    /** Record {@link #FULL_SQUAD} for an online owner. */
    public static void recordFullSquad(ServerPlayer owner) {
        ProgressionGates.open(owner, FULL_SQUAD);
    }

    /** Record {@link #FULL_SQUAD} for an owner who may be offline. */
    public static void recordFullSquad(MinecraftServer server, UUID owner) {
        ProgressionGates.open(server, owner, FULL_SQUAD);
    }
}
