package za.co.neroland.nerocompanion.progression;

import java.util.List;
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
 * <p>The real events open them through {@link CompanionProgress} (deploy, chip install, charge, a unit of mining /
 * carrying / repair / guard work, a Scrapling hatch or NeroCreatures pet adoption, bond level 3, a reclaimed lost
 * companion, and the LIVE count reaching the cap); the {@code record...} methods below stay for callers that hold an
 * online player.
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
    /** A charger (Pad, Bay or Hand Crank) put energy into one of the owner's companions. */
    public static final Identifier FIRST_CHARGE = gate("first_charge");
    /** A Mining Bot of the owner finished a unit of mining work. */
    public static final Identifier FIRST_MINE = gate("first_mine");
    /** A carrier of the owner delivered cargo. */
    public static final Identifier FIRST_CARRY = gate("first_carry");
    /** A Repair Bot of the owner finished a repair. */
    public static final Identifier FIRST_REPAIR = gate("first_repair");
    /** A Combat Bot of the owner downed a target while guarding. */
    public static final Identifier FIRST_GUARD = gate("first_guard");
    /** A Scrap Egg of the owner hatched into a Scrapling. */
    public static final Identifier FIRST_HATCH = gate("first_hatch");
    /** One of the owner's companions reached bond level 3. */
    public static final Identifier BOND_THREE = gate("bond_three");
    /** The owner reclaimed a lost companion. */
    public static final Identifier FIRST_RECOVERY = gate("first_recovery");

    /** Every milestone gate this jar ships ({@code data/nerocompanion/neroland_gates/<path>.json}). */
    public static final List<Identifier> ALL = List.of(FIRST_DEPLOY, FIRST_CHIP, PET_ADOPTED, FULL_SQUAD, FIRST_CHARGE,
            FIRST_MINE, FIRST_CARRY, FIRST_REPAIR, FIRST_GUARD, FIRST_HATCH, BOND_THREE, FIRST_RECOVERY);

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
