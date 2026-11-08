package za.co.neroland.nerocompanion.entity;

/**
 * Which environmental damage a companion ignores. Companions are machines, and until lost-companion recovery
 * exists a death is permanent, so nothing a sleeping or just-unperched companion cannot react to may kill it.
 *
 * <ul>
 *   <li>Always immune: drowning, suffocation in a block, falling. A companion never needs air, and a flyer that
 *       was perched or asleep cannot steer.</li>
 *   <li>Immune while asleep: fire, lava, magma floors and contact damage (cactus, berry bushes, dripstone).
 *       Asleep it cannot move out of them. Awake it takes this damage like any mob, and its goals avoid it.</li>
 *   <li>Never immune: attacks, explosions, the void, commands - anything that is not the environment.</li>
 * </ul>
 *
 * <p>Pure Java (the damage source is classified by the entity) so the rule is unit-tested.
 */
public final class CompanionSafety {

    /** A damage source, classified. */
    public enum Hazard {
        DROWNING,
        IN_WALL,
        FALL,
        FIRE,
        CONTACT,
        OTHER
    }

    private CompanionSafety() {
    }

    public static boolean isImmune(Hazard hazard, boolean sleeping) {
        return switch (hazard) {
            case DROWNING, IN_WALL, FALL -> true;
            case FIRE, CONTACT -> sleeping;
            case OTHER -> false;
        };
    }
}
