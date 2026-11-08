package za.co.neroland.nerocompanion.entity.type;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;

/**
 * A companion chassis, loaded from {@code data/<namespace>/companion_types/<id>.json}. Stats,
 * supported modes, chip slots, default skin and AI tuning are data so modpacks can retune them; adding a
 * wholly new chassis still needs an entity registration.
 *
 * @param id               the type id (the file's resource id)
 * @param entity           the companion {@code EntityType} this chassis drives
 * @param locomotion       must match the entity class
 * @param small            may perch on the owner's shoulder
 * @param energy           FE buffer and per-second drains
 * @param modes            supported modes; {@link CompanionMode#SLEEP} is always included
 * @param requiresGate     optional Core progression gate per mode
 * @param cargoSlots       item slots the chassis carries ({@code cargo_slots}, default 0); chips may add more
 * @param pickupRadius     loose-item pickup radius in blocks for carrying work ({@code pickup_radius}, default 8)
 */
public record CompanionType(
        Identifier id,
        Identifier entity,
        Locomotion locomotion,
        boolean small,
        double maxHealth,
        double armor,
        double speed,
        double flyingSpeed,
        double attackDamage,
        Energy energy,
        Set<CompanionMode> modes,
        CompanionMode defaultMode,
        int chipSlots,
        Set<ChipCategory> chipCategories,
        Optional<Identifier> defaultSkin,
        GoalWeights goalWeights,
        List<Identifier> workGoals,
        Map<CompanionMode, Identifier> requiresGate,
        int cargoSlots,
        int pickupRadius) {

    public CompanionType {
        EnumSet<CompanionMode> copy = EnumSet.noneOf(CompanionMode.class);
        copy.addAll(modes);
        copy.add(CompanionMode.SLEEP);
        modes = Set.copyOf(copy);
        chipCategories = Set.copyOf(chipCategories);
        workGoals = List.copyOf(workGoals);
        requiresGate = Map.copyOf(requiresGate);
        cargoSlots = Math.max(0, cargoSlots);
        pickupRadius = Math.max(1, Math.min(CompanionTypeParser.MAX_PICKUP_RADIUS, pickupRadius));
    }

    /** FE capacity and drains per second. SLEEP drains nothing. */
    public record Energy(long capacity, long drainIdlePerSecond, long drainActivePerSecond) {
    }

    /** AI tuning: stop-following distance, teleport-if-farther distance, and the idle wander chance per check. */
    public record GoalWeights(double followDistance, double teleportDistance, double wanderChance) {
    }

    public boolean supports(CompanionMode mode) {
        return this.modes.contains(mode);
    }

    /** The built-in default chassis for a locomotion; every JSON field falls back to these values. */
    public static CompanionType defaults(Identifier id, Identifier entity, Locomotion locomotion) {
        boolean flying = locomotion == Locomotion.FLYING;
        return new CompanionType(id, entity, locomotion, flying,
                flying ? 10.0D : 20.0D, 0.0D, flying ? 0.3D : 0.25D, flying ? 0.5D : 0.0D, 0.0D,
                new Energy(24_000L, 10L, 40L),
                EnumSet.of(CompanionMode.FOLLOW, CompanionMode.STAY, CompanionMode.SLEEP),
                CompanionMode.FOLLOW, 2,
                EnumSet.allOf(ChipCategory.class), Optional.empty(),
                new GoalWeights(3.0D, 16.0D, 0.05D), List.of(), Map.of(), 0, CompanionTypeParser.DEFAULT_PICKUP_RADIUS);
    }
}
