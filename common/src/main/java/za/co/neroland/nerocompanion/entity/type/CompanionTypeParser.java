package za.co.neroland.nerocompanion.entity.type;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;

/**
 * Reads one companion type JSON into a {@link CompanionType}, collecting {@link ValidationIssue}s instead of
 * throwing. Pure: it takes the entity lookup as a function, so it runs in unit tests without a game.
 *
 * <p>Rules: an entity id that is not a registered companion entity drops the file; every
 * other bad value - unknown mode, unknown chip category, unknown work goal, locomotion mismatch, out-of-range
 * number - is an issue and that field keeps the chassis default.
 */
public final class CompanionTypeParser {

    /** Hard upper bound for chip slots; the Stage 2 chip screen draws at most this many. */
    public static final int MAX_CHIP_SLOTS = 6;

    /** Hard upper bound for a chassis' own cargo slots (a double chest). */
    public static final int MAX_CARGO_SLOTS = 54;

    /** Pickup radius bounds and default (blocks). */
    public static final int MAX_PICKUP_RADIUS = 16;
    public static final int DEFAULT_PICKUP_RADIUS = 8;

    private CompanionTypeParser() {
    }

    /**
     * @param id             the type id (file resource id)
     * @param json           the file contents
     * @param entityLookup   companion entity id to its locomotion; empty if the id is not a companion entity
     * @param knownWorkGoals work goal ids the mod provides
     * @param issues         receives every problem found
     * @return the type, or empty when the file was dropped
     */
    public static Optional<CompanionType> parse(Identifier id, JsonElement json,
            Function<Identifier, Optional<Locomotion>> entityLookup, Set<Identifier> knownWorkGoals,
            List<ValidationIssue> issues) {
        if (json == null || !json.isJsonObject()) {
            issues.add(dropped(id, "(root)", "not a JSON object"));
            return Optional.empty();
        }
        JsonObject root = json.getAsJsonObject();
        Optional<Identifier> entity = identifier(root, "entity");
        if (entity.isEmpty()) {
            issues.add(dropped(id, "entity", "missing or not a resource id"));
            return Optional.empty();
        }
        Optional<Locomotion> actual = entityLookup.apply(entity.get());
        if (actual.isEmpty()) {
            issues.add(dropped(id, "entity", entity.get() + " is not a registered companion entity"));
            return Optional.empty();
        }
        Locomotion locomotion = actual.get();
        CompanionType base = CompanionType.defaults(id, entity.get(), locomotion);

        if (root.has("locomotion")) {
            Optional<Locomotion> declared = string(root, "locomotion").flatMap(Locomotion::byId);
            if (declared.isEmpty()) {
                issues.add(defaulted(id, "locomotion", "must be \"flying\" or \"ground\""));
            } else if (declared.get() != locomotion) {
                issues.add(defaulted(id, "locomotion", "is " + declared.get().id() + " but " + entity.get()
                        + " is a " + locomotion.id() + " companion"));
            }
        }

        boolean small = bool(root, "small", base.small(), id, issues);
        double maxHealth = number(root, "max_health", base.maxHealth(), 1.0D, 1024.0D, id, issues);
        double armor = number(root, "armor", base.armor(), 0.0D, 30.0D, id, issues);
        double speed = number(root, "speed", base.speed(), 0.0D, 2.0D, id, issues);
        double flyingSpeed = number(root, "flying_speed", base.flyingSpeed(), 0.0D, 4.0D, id, issues);
        double attackDamage = number(root, "attack_damage", base.attackDamage(), 0.0D, 64.0D, id, issues);

        CompanionType.Energy energy = base.energy();
        if (root.has("energy")) {
            if (root.get("energy").isJsonObject()) {
                JsonObject e = root.getAsJsonObject("energy");
                energy = new CompanionType.Energy(
                        (long) number(e, "capacity", energy.capacity(), 1.0D, 100_000_000.0D, id, issues, "energy."),
                        (long) number(e, "drain_idle_per_second", energy.drainIdlePerSecond(), 0.0D, 1_000_000.0D,
                                id, issues, "energy."),
                        (long) number(e, "drain_active_per_second", energy.drainActivePerSecond(), 0.0D,
                                1_000_000.0D, id, issues, "energy."));
            } else {
                issues.add(defaulted(id, "energy", "must be an object"));
            }
        }

        Set<CompanionMode> modes = EnumSet.copyOf(base.modes());
        if (root.has("modes")) {
            Optional<JsonArray> array = array(root, "modes");
            if (array.isEmpty()) {
                issues.add(defaulted(id, "modes", "must be an array of mode ids"));
            } else {
                EnumSet<CompanionMode> read = EnumSet.noneOf(CompanionMode.class);
                for (JsonElement element : array.get()) {
                    String name = element.isJsonPrimitive() ? element.getAsString() : String.valueOf(element);
                    Optional<CompanionMode> mode = CompanionMode.byId(name);
                    if (mode.isPresent()) {
                        read.add(mode.get());
                    } else {
                        issues.add(defaulted(id, "modes", "unknown mode \"" + name + "\" ignored"));
                    }
                }
                read.add(CompanionMode.SLEEP);
                if (read.size() == 1) {
                    issues.add(defaulted(id, "modes", "no usable mode listed"));
                } else {
                    modes = read;
                }
            }
        }

        CompanionMode defaultMode = modes.contains(base.defaultMode()) ? base.defaultMode() : firstAwake(modes);
        if (root.has("default_mode")) {
            Optional<CompanionMode> declared = string(root, "default_mode").flatMap(CompanionMode::byId);
            if (declared.isEmpty()) {
                issues.add(defaulted(id, "default_mode", "unknown mode"));
            } else if (!modes.contains(declared.get())) {
                issues.add(defaulted(id, "default_mode", declared.get().id() + " is not in \"modes\""));
            } else {
                defaultMode = declared.get();
            }
        }

        int chipSlots = (int) number(root, "chip_slots", base.chipSlots(), 0.0D, MAX_CHIP_SLOTS, id, issues);
        int cargoSlots = (int) number(root, "cargo_slots", base.cargoSlots(), 0.0D, MAX_CARGO_SLOTS, id, issues);
        int pickupRadius = (int) number(root, "pickup_radius", base.pickupRadius(), 1.0D, MAX_PICKUP_RADIUS, id,
                issues);

        Set<ChipCategory> categories = EnumSet.copyOf(base.chipCategories());
        if (root.has("chip_categories")) {
            Optional<JsonArray> array = array(root, "chip_categories");
            if (array.isEmpty()) {
                issues.add(defaulted(id, "chip_categories", "must be an array"));
            } else {
                EnumSet<ChipCategory> read = EnumSet.noneOf(ChipCategory.class);
                for (JsonElement element : array.get()) {
                    String name = element.isJsonPrimitive() ? element.getAsString() : String.valueOf(element);
                    Optional<ChipCategory> category = ChipCategory.byId(name);
                    if (category.isPresent()) {
                        read.add(category.get());
                    } else {
                        issues.add(defaulted(id, "chip_categories", "unknown chip category \"" + name + "\" ignored"));
                    }
                }
                categories = read;
            }
        }

        Optional<Identifier> defaultSkin = base.defaultSkin();
        if (root.has("default_skin")) {
            defaultSkin = identifier(root, "default_skin");
            if (defaultSkin.isEmpty()) {
                issues.add(defaulted(id, "default_skin", "not a resource id"));
            }
        }

        CompanionType.GoalWeights weights = base.goalWeights();
        if (root.has("goal_weights")) {
            if (root.get("goal_weights").isJsonObject()) {
                JsonObject w = root.getAsJsonObject("goal_weights");
                double follow = number(w, "follow_distance", weights.followDistance(), 1.0D, 32.0D, id, issues,
                        "goal_weights.");
                double teleport = number(w, "teleport_distance", weights.teleportDistance(), 4.0D, 64.0D, id,
                        issues, "goal_weights.");
                double wander = number(w, "wander_chance", weights.wanderChance(), 0.0D, 1.0D, id, issues,
                        "goal_weights.");
                if (teleport <= follow) {
                    issues.add(defaulted(id, "goal_weights.teleport_distance",
                            "must be larger than follow_distance"));
                    teleport = Math.max(weights.teleportDistance(), follow + 4.0D);
                }
                weights = new CompanionType.GoalWeights(follow, teleport, wander);
            } else {
                issues.add(defaulted(id, "goal_weights", "must be an object"));
            }
        }

        List<Identifier> workGoals = new ArrayList<>(base.workGoals());
        if (root.has("work_goals")) {
            Optional<JsonArray> array = array(root, "work_goals");
            if (array.isEmpty()) {
                issues.add(defaulted(id, "work_goals", "must be an array"));
            } else {
                workGoals.clear();
                for (JsonElement element : array.get()) {
                    Identifier goal = element.isJsonPrimitive() ? Identifier.tryParse(element.getAsString()) : null;
                    if (goal == null || !knownWorkGoals.contains(goal)) {
                        issues.add(defaulted(id, "work_goals", "unknown work goal " + element + " ignored"));
                    } else {
                        workGoals.add(goal);
                    }
                }
            }
        }

        Map<CompanionMode, Identifier> gates = new EnumMap<>(CompanionMode.class);
        if (root.has("requires_gate")) {
            if (root.get("requires_gate").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("requires_gate").entrySet()) {
                    Optional<CompanionMode> mode = CompanionMode.byId(entry.getKey());
                    Identifier gate = entry.getValue().isJsonPrimitive()
                            ? Identifier.tryParse(entry.getValue().getAsString()) : null;
                    if (mode.isEmpty() || gate == null) {
                        issues.add(defaulted(id, "requires_gate", "entry \"" + entry.getKey() + "\" ignored"));
                    } else {
                        gates.put(mode.get(), gate);
                    }
                }
            } else {
                issues.add(defaulted(id, "requires_gate", "must be an object of mode -> gate id"));
            }
        }

        return Optional.of(new CompanionType(id, entity.get(), locomotion, small, maxHealth, armor, speed,
                flyingSpeed, attackDamage, energy, modes, defaultMode, chipSlots, categories, defaultSkin, weights,
                workGoals, gates, cargoSlots, pickupRadius));
    }

    private static CompanionMode firstAwake(Set<CompanionMode> modes) {
        for (CompanionMode mode : CompanionMode.values()) {
            if (mode != CompanionMode.SLEEP && modes.contains(mode)) {
                return mode;
            }
        }
        return CompanionMode.SLEEP;
    }

    private static ValidationIssue dropped(Identifier id, String field, String message) {
        return new ValidationIssue(ValidationIssue.Severity.DROPPED, id, field, message);
    }

    private static ValidationIssue defaulted(Identifier id, String field, String message) {
        return new ValidationIssue(ValidationIssue.Severity.FIELD_DEFAULTED, id, field, message);
    }

    private static Optional<String> string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private static Optional<Identifier> identifier(JsonObject object, String key) {
        return string(object, key).map(Identifier::tryParse);
    }

    private static Optional<JsonArray> array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? Optional.of(element.getAsJsonArray()) : Optional.empty();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback, Identifier id,
            List<ValidationIssue> issues) {
        JsonElement element = object.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            issues.add(defaulted(id, key, "must be true or false"));
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static double number(JsonObject object, String key, double fallback, double min, double max,
            Identifier id, List<ValidationIssue> issues) {
        return number(object, key, fallback, min, max, id, issues, "");
    }

    private static double number(JsonObject object, String key, double fallback, double min, double max,
            Identifier id, List<ValidationIssue> issues, String prefix) {
        JsonElement element = object.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            issues.add(defaulted(id, prefix + key, "must be a number"));
            return fallback;
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || value < min || value > max) {
            issues.add(defaulted(id, prefix + key, "out of range [" + min + ", " + max + "]"));
            return fallback;
        }
        return value;
    }
}
