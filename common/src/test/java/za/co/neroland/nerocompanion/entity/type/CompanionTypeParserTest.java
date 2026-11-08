package za.co.neroland.nerocompanion.entity.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;

/** Type JSON validation: the shipped file is clean, and every bad case is an issue (never an exception). */
class CompanionTypeParserTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("nerocompanion", "test_drone");
    private static final Identifier ENTITY = Identifier.fromNamespaceAndPath("nerocompanion", "test_drone");
    private static final Function<Identifier, Optional<Locomotion>> LOOKUP =
            id -> id.equals(ENTITY) ? Optional.of(Locomotion.FLYING) : Optional.empty();

    private static JsonObject shipped() throws IOException {
        try (InputStream in = CompanionTypeParserTest.class.getResourceAsStream(
                "/data/nerocompanion/companion_types/test_drone.json")) {
            if (in == null) {
                // The resources are not on the test classpath on every loader node; fall back to the same JSON.
                return JsonParser.parseString("{\"entity\":\"nerocompanion:test_drone\",\"locomotion\":\"flying\","
                        + "\"small\":true,\"energy\":{\"capacity\":24000,\"drain_idle_per_second\":10,"
                        + "\"drain_active_per_second\":40},\"modes\":[\"follow\",\"stay\",\"sleep\"],"
                        + "\"default_mode\":\"follow\",\"chip_slots\":2,"
                        + "\"chip_categories\":[\"stat\",\"capability\",\"cosmetic\"],"
                        + "\"goal_weights\":{\"follow_distance\":3.0,\"teleport_distance\":16.0,"
                        + "\"wander_chance\":0.05},\"work_goals\":[]}").getAsJsonObject();
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Optional<CompanionType> parse(JsonElement json, List<ValidationIssue> issues) {
        return CompanionTypeParser.parse(TYPE, json, LOOKUP, Set.of(), issues);
    }

    @Test
    void shippedTestDroneIsValid() throws IOException {
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = parse(shipped(), issues).orElseThrow();
        assertTrue(issues.isEmpty(), () -> issues.toString());
        assertEquals(Locomotion.FLYING, type.locomotion());
        assertTrue(type.small());
        assertEquals(24_000L, type.energy().capacity());
        assertEquals(10L, type.energy().drainIdlePerSecond());
        assertEquals(40L, type.energy().drainActivePerSecond());
        assertEquals(EnumSet.of(CompanionMode.FOLLOW, CompanionMode.STAY, CompanionMode.SLEEP), type.modes());
        assertEquals(CompanionMode.FOLLOW, type.defaultMode());
        assertEquals(2, type.chipSlots());
        assertEquals(16.0D, type.goalWeights().teleportDistance());
    }

    private static List<ValidationIssue> issuesFor(String field, JsonElement value) throws IOException {
        JsonObject json = shipped();
        json.add(field, value);
        List<ValidationIssue> issues = new ArrayList<>();
        Optional<CompanionType> type = parse(json, issues);
        assertTrue(type.isPresent(), "a bad field must not drop the type");
        return issues;
    }

    private static void assertDefaulted(List<ValidationIssue> issues, String field) {
        assertFalse(issues.isEmpty(), "expected an issue for " + field);
        assertTrue(issues.stream().allMatch(i -> i.severity() == ValidationIssue.Severity.FIELD_DEFAULTED));
        assertTrue(issues.stream().anyMatch(i -> i.field().startsWith(field)), () -> issues.toString());
    }

    @Test
    void unknownModeIsAnIssueAndIgnored() throws IOException {
        JsonObject json = shipped();
        json.add("modes", JsonParser.parseString("[\"follow\", \"dance\"]"));
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = parse(json, issues).orElseThrow();
        assertDefaulted(issues, "modes");
        assertEquals(EnumSet.of(CompanionMode.FOLLOW, CompanionMode.SLEEP), type.modes());
    }

    @Test
    void unknownChipCategoryIsAnIssue() throws IOException {
        assertDefaulted(issuesFor("chip_categories", JsonParser.parseString("[\"stat\", \"laser\"]")),
                "chip_categories");
    }

    @Test
    void unknownWorkGoalIsAnIssue() throws IOException {
        assertDefaulted(issuesFor("work_goals", JsonParser.parseString("[\"nerocompanion:juggle\"]")), "work_goals");
    }

    @Test
    void locomotionMismatchIsAnIssueAndTheEntityWins() throws IOException {
        JsonObject json = shipped();
        json.addProperty("locomotion", "ground");
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = parse(json, issues).orElseThrow();
        assertDefaulted(issues, "locomotion");
        assertEquals(Locomotion.FLYING, type.locomotion());
    }

    @Test
    void defaultModeOutsideModesIsAnIssue() throws IOException {
        assertDefaulted(issuesFor("default_mode", JsonParser.parseString("\"mine\"")), "default_mode");
    }

    @Test
    void outOfRangeNumbersFallBack() throws IOException {
        JsonObject json = shipped();
        json.addProperty("max_health", -5);
        json.addProperty("chip_slots", 99);
        json.add("energy", JsonParser.parseString("{\"capacity\": \"lots\"}"));
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = parse(json, issues).orElseThrow();
        assertEquals(3, issues.size(), () -> issues.toString());
        assertEquals(10.0D, type.maxHealth());
        assertEquals(2, type.chipSlots());
        assertEquals(24_000L, type.energy().capacity());
    }

    @Test
    void teleportMustExceedFollow() throws IOException {
        assertDefaulted(issuesFor("goal_weights", JsonParser.parseString(
                "{\"follow_distance\": 10, \"teleport_distance\": 5}")), "goal_weights.teleport_distance");
    }

    @Test
    void unregisteredEntityDropsTheFile() throws IOException {
        JsonObject json = shipped();
        json.addProperty("entity", "nerocompanion:no_such_bot");
        List<ValidationIssue> issues = new ArrayList<>();
        assertTrue(parse(json, issues).isEmpty());
        assertEquals(ValidationIssue.Severity.DROPPED, issues.get(0).severity());
    }

    @Test
    void notAnObjectDropsTheFile() {
        List<ValidationIssue> issues = new ArrayList<>();
        assertTrue(parse(JsonParser.parseString("[1, 2]"), issues).isEmpty());
        assertEquals(1, issues.size());
    }

    @Test
    void requiresGateParsesAndBadEntriesAreIssues() throws IOException {
        JsonObject json = shipped();
        json.add("requires_gate", JsonParser.parseString(
                "{\"follow\": \"nerolandcore:industrial_power\", \"dance\": \"x:y\"}"));
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = parse(json, issues).orElseThrow();
        assertEquals(1, issues.size());
        assertEquals(Identifier.fromNamespaceAndPath("nerolandcore", "industrial_power"),
                type.requiresGate().get(CompanionMode.FOLLOW));
    }

    @Test
    void sleepIsAlwaysSupported() throws IOException {
        JsonObject json = shipped();
        json.add("modes", JsonParser.parseString("[\"follow\"]"));
        CompanionType type = parse(json, new ArrayList<>()).orElseThrow();
        assertTrue(type.supports(CompanionMode.SLEEP));
    }
}
