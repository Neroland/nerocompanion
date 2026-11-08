package za.co.neroland.nerocreatures.compat;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * <b>Compile-time stub, never packaged.</b> A signature-for-signature copy of NeroCreatures' published service
 * interface (NeroCreatures 0.4.1-alpha.2, four default methods on vanilla types), so NeroCompanion's
 * {@code compat.creatures.CompanionPetHooksImpl} compiles without a NeroCreatures artifact. Each loader build puts
 * this source set on the main and test classpaths only; the mod jar never contains this class. At runtime the real
 * interface comes from NeroCreatures, which is the only code that ever looks the implementation up (through
 * {@link java.util.ServiceLoader}, and only when NeroCompanion is loaded). Keep it identical to the published
 * interface: a method added there needs no change here (it is a default), a method changed there must be copied.
 */
public interface CompanionPetHooks {

    /** A pet has just been tamed and indexed against its owner (server thread). */
    default void onPetTamed(TamableAnimal pet, Player owner) {
    }

    /** The owner cycled their pet's command: {@code "sit"}, {@code "guard"} or {@code "follow"}. */
    default void onPetCommandChanged(TamableAnimal pet, String command) {
    }

    /** A pet stopped belonging to anyone (died, or its owner's data was erased). Drop any state kept for it. */
    default void onPetReleased(TamableAnimal pet) {
    }

    /** Whether the companion mod is driving this pet's idle behaviour right now (suppresses the species perk). */
    default boolean overridesIdleBehaviour(TamableAnimal pet) {
        return false;
    }
}
