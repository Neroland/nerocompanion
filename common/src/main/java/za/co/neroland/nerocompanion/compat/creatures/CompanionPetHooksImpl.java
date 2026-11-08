package za.co.neroland.nerocompanion.compat.creatures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerocompanion.progression.CompanionMilestones;
import za.co.neroland.nerocreatures.compat.CompanionPetHooks;

/**
 * NeroCompanion's side of NeroCreatures' pet SPI. NeroCreatures finds this class through
 * {@link java.util.ServiceLoader} ({@code META-INF/services/za.co.neroland.nerocreatures.compat.CompanionPetHooks})
 * during its own common init, and only when NeroCompanion is loaded.
 *
 * <p><b>Loading rule.</b> This is the only NeroCompanion class that names a NeroCreatures type, and no NeroCompanion
 * code path refers to it: it is reached through the service file alone, so without NeroCreatures it is never
 * loaded. Everything it learns goes into {@link CreaturePets}, which has no NeroCreatures types and is safe for the
 * rest of the mod to read.
 *
 * <p><b>Construction.</b> The no-arg constructor does nothing: NeroCreatures instantiates this class during its mod
 * construction, possibly before NeroCompanion's own init has run, so no registry, config or static of
 * NeroCompanion is touched until a callback arrives on the server thread.
 *
 * <p><b>What today's SPI allows</b> (four methods): the per-owner pet list for the app listing and a bond seam
 * ({@link #onPetTamed}, {@link #onPetCommandChanged}, {@link #onPetReleased}). It never drives the pet's idle
 * behaviour ({@link #overridesIdleBehaviour} is false): NeroCompanion adds no goals to NeroCreatures pets in 0.5.0,
 * so standing NeroCreatures' species perk down would only take something away. Perch, fetch, Bay parking, recovery
 * and hats need additive SPI methods, proposed to NeroCreatures as a handover; they are cuts in 0.5.0.
 *
 * <p>Privacy: the callbacks pass live objects; only the owner's UUID, the pet's entity UUID, its type id and its
 * command are kept, in memory, and erased with the owner ({@link CreaturePets#forgetOwner}).
 */
public final class CompanionPetHooksImpl implements CompanionPetHooks {

    /** Side-effect free: see the class comment. */
    public CompanionPetHooksImpl() {
    }

    @Override
    public void onPetTamed(TamableAnimal pet, Player owner) {
        if (pet == null || owner == null || !(pet.level() instanceof ServerLevel)) {
            return;
        }
        CreaturePets.tamed(owner.getUUID(), pet.getUUID(), EntityType.getKey(pet.getType()).toString());
        if (owner instanceof ServerPlayer player) {
            try {
                CompanionMilestones.recordPetAdopted(player);
            } catch (RuntimeException failure) {
                // A milestone must never break NeroCreatures' taming path.
            }
        }
    }

    @Override
    public void onPetCommandChanged(TamableAnimal pet, String command) {
        if (pet == null || !(pet.level() instanceof ServerLevel)) {
            return;
        }
        CreaturePets.command(pet.getUUID(), command);
    }

    @Override
    public void onPetReleased(TamableAnimal pet) {
        if (pet == null || !(pet.level() instanceof ServerLevel)) {
            return;
        }
        CreaturePets.released(pet.getUUID());
    }

    @Override
    public boolean overridesIdleBehaviour(TamableAnimal pet) {
        return false;
    }
}
