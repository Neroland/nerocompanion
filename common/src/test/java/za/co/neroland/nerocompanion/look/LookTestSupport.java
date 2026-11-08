package za.co.neroland.nerocompanion.look;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.type.ValidationIssue;

/** Shared fixtures for the look tests: ids, the type set, and a configurable unlock context. */
final class LookTestSupport {

    static final Identifier DRONE = id("personal_drone");
    static final Identifier SCOUT = id("scout_drone");
    static final Identifier SCRAPLING = id("scrapling");
    static final Set<Identifier> TYPES = Set.of(DRONE, SCOUT, SCRAPLING, id("mining_bot"));

    private LookTestSupport() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion", path);
    }

    static Optional<Look> parse(String path, LookKind kind, String json, List<ValidationIssue> issues) {
        return LookParser.parse(id(path), kind, JsonParser.parseString(json), TYPES, Looks.MODEL_OVERRIDES, issues);
    }

    static Look look(String path, LookKind kind, String json) {
        List<ValidationIssue> issues = new ArrayList<>();
        return parse(path, kind, json, issues).orElseThrow(() -> new AssertionError(issues.toString()));
    }

    /** A context whose answers the test sets. */
    static class Ctx implements LookUnlocks.Context {
        int bond;
        Set<Identifier> done = Set.of();
        Set<Identifier> items = Set.of();
        String season = "none";
        Set<Identifier> hooks = Set.of();

        @Override
        public int bondLevel() {
            return this.bond;
        }

        @Override
        public boolean advancementDone(Identifier id) {
            return this.done.contains(id);
        }

        @Override
        public boolean hasItem(Identifier id) {
            return this.items.contains(id);
        }

        @Override
        public String season() {
            return this.season;
        }

        @Override
        public boolean hookOpen(Identifier id) {
            return this.hooks.contains(id);
        }
    }
}
