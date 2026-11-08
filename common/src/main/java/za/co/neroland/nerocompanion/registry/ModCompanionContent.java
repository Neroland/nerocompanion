package za.co.neroland.nerocompanion.registry;

/**
 * The one place per-type companion code is switched on: work goals ({@code ai.work.WorkGoals}), job codecs
 * ({@code job.JobCodecs}), Remote sneak-use handlers ({@code item.RemoteBlockActions}) and the personality hooks
 * ({@code personality.PersonalityHooks.set}). Each package exposes one static {@code register()} that does its own
 * registrations; this class calls them, in a fixed order, from common init step 3 - before any type file is read
 * (type files are read lazily on the server, so every work-goal id is known by then).
 */
public final class ModCompanionContent {

    private static boolean done;

    private ModCompanionContent() {
    }

    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        // Work goals, job codecs and Remote block actions, one package per line.
        za.co.neroland.nerocompanion.ai.drone.DroneWork.register();
        za.co.neroland.nerocompanion.ai.mining.MiningWork.register();
        za.co.neroland.nerocompanion.ai.carry.CarryWork.register();
        za.co.neroland.nerocompanion.ai.repair.RepairWork.register();
        za.co.neroland.nerocompanion.ai.combat.CombatWork.register();
        za.co.neroland.nerocompanion.ai.pet.PetWork.register();
        // The guard protocol chip unlocks GUARD on any chassis, so it brings the guard goals with it.
        za.co.neroland.nerocompanion.ai.work.WorkGoals.grantByChip(
                za.co.neroland.nerocompanion.chip.ChipKinds.GUARD_PROTOCOL.id(),
                java.util.List.of(za.co.neroland.nerocompanion.ai.combat.CombatWork.GUARD_TARGET,
                        za.co.neroland.nerocompanion.ai.combat.CombatWork.GUARD_MELEE));
        // Personality and bond.
        za.co.neroland.nerocompanion.personality.PersonalityHooksImpl.register();
        za.co.neroland.nerocompanion.bond.BondService.useWriter(
                za.co.neroland.nerocompanion.entity.CompanionEntity::setBondPoints);
    }
}
