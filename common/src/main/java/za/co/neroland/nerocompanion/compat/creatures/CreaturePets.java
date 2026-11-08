package za.co.neroland.nerocompanion.compat.creatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * NeroCreatures pets NeroCompanion knows about, per owner: what the SPI callbacks reported since the server
 * started. It names no NeroCreatures type, so any NeroCompanion code (the link roster, a bond counter) may read it
 * whether or not NeroCreatures is installed; without NeroCreatures it simply stays empty.
 *
 * <p>Server memory only: never saved, never sent. A pet tamed before this server start appears only after its next
 * SPI event (there is no "pet loaded" hook yet; proposed in the NeroCreatures handover). Bounded:
 * {@value #MAX_PER_OWNER} pets per owner (oldest dropped). Personal data: owner UUID -> pet ids; erased with
 * {@link #forgetOwner} through Core's {@code PlayerDataErasure}, cleared with {@link #clear} on server stop.
 */
public final class CreaturePets {

    /** Most pets tracked per owner. */
    public static final int MAX_PER_OWNER = 64;

    /** Commands NeroCreatures reports ({@code CompanionPetHooks#onPetCommandChanged}). */
    public static final Set<String> COMMANDS = Set.of("sit", "guard", "follow");

    /** One tracked pet. {@code bondPoints} is a seam for a later bond counter (always 0 in 0.5.0). */
    public record Pet(UUID petId, String type, String command, int bondPoints) {
    }

    private static final Map<UUID, LinkedHashMap<UUID, Pet>> BY_OWNER = new HashMap<>();
    private static final Map<UUID, UUID> OWNER_OF = new HashMap<>();

    private CreaturePets() {
    }

    /** A pet was tamed (or re-tamed by someone else): it now belongs to {@code owner}, commanded to follow. */
    public static synchronized void tamed(UUID owner, UUID pet, String type) {
        if (owner == null || pet == null) {
            return;
        }
        released(pet);
        LinkedHashMap<UUID, Pet> pets = BY_OWNER.computeIfAbsent(owner, key -> new LinkedHashMap<>());
        pets.put(pet, new Pet(pet, type == null ? "" : type, "follow", 0));
        OWNER_OF.put(pet, owner);
        while (pets.size() > MAX_PER_OWNER) {
            UUID oldest = pets.keySet().iterator().next();
            pets.remove(oldest);
            OWNER_OF.remove(oldest);
        }
    }

    /** The owner cycled the pet's command; unknown pets and unknown commands are ignored. */
    public static synchronized void command(UUID pet, String command) {
        UUID owner = OWNER_OF.get(pet);
        if (owner == null || command == null || !COMMANDS.contains(command)) {
            return;
        }
        LinkedHashMap<UUID, Pet> pets = BY_OWNER.get(owner);
        if (pets == null) {
            return;
        }
        Pet current = pets.get(pet);
        if (current != null) {
            pets.put(pet, new Pet(pet, current.type(), command, current.bondPoints()));
        }
    }

    /** The pet died or was returned to the wild: forget it. */
    public static synchronized void released(UUID pet) {
        UUID owner = OWNER_OF.remove(pet);
        if (owner == null) {
            return;
        }
        LinkedHashMap<UUID, Pet> pets = BY_OWNER.get(owner);
        if (pets != null) {
            pets.remove(pet);
            if (pets.isEmpty()) {
                BY_OWNER.remove(owner);
            }
        }
    }

    /** {@code owner}'s known pets, oldest first (a copy). */
    public static synchronized List<Pet> petsOf(UUID owner) {
        LinkedHashMap<UUID, Pet> pets = BY_OWNER.get(owner);
        return pets == null ? List.of() : List.copyOf(new ArrayList<>(pets.values()));
    }

    /** Who owns {@code pet}, if known. */
    public static synchronized Optional<UUID> ownerOf(UUID pet) {
        return Optional.ofNullable(OWNER_OF.get(pet));
    }

    /** Erasure: forget every pet of {@code owner}; returns how many. */
    public static synchronized int forgetOwner(UUID owner) {
        LinkedHashMap<UUID, Pet> pets = BY_OWNER.remove(owner);
        if (pets == null) {
            return 0;
        }
        pets.keySet().forEach(OWNER_OF::remove);
        return pets.size();
    }

    /** Pets tracked across all owners. */
    public static synchronized int size() {
        return OWNER_OF.size();
    }

    /** Server stopped (or a test): forget everything. */
    public static synchronized void clear() {
        BY_OWNER.clear();
        OWNER_OF.clear();
    }
}
