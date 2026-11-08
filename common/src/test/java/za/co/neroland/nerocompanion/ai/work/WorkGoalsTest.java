package za.co.neroland.nerocompanion.ai.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.type.CompanionTypeParser;
import za.co.neroland.nerocompanion.entity.type.ValidationIssue;

/** The work-goal registry, the mode selection the controller uses, and the type fields Stage 3 added. */
class WorkGoalsTest {

    private static final Identifier ENTITY = id("bot");
    private static final Function<Identifier, Optional<Locomotion>> LOOKUP =
            entity -> entity.equals(ENTITY) ? Optional.of(Locomotion.GROUND) : Optional.empty();

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion_test", path);
    }

    private static WorkGoalFactory factory(CompanionMode... modes) {
        EnumSet<CompanionMode> set = EnumSet.noneOf(CompanionMode.class);
        set.addAll(List.of(modes));
        return WorkGoalFactory.of(set, (companion, type) -> {
            throw new AssertionError("not created in a unit test");
        });
    }

    private static synchronized void ensure(Identifier goal, CompanionMode... modes) {
        if (WorkGoals.get(goal).isEmpty()) {
            WorkGoals.register(goal, factory(modes));
        }
    }

    @Test
    void registeredIdsAreKnownAndDuplicatesAreRejected() {
        Identifier goal = id("registry_mine");
        WorkGoals.register(goal, factory(CompanionMode.MINE));
        assertTrue(WorkGoals.known().contains(goal));
        assertTrue(WorkGoals.get(goal).isPresent());
        assertThrows(IllegalStateException.class, () -> WorkGoals.register(goal, factory(CompanionMode.MINE)));
        assertFalse(WorkGoals.get(id("never_registered")).isPresent());
    }

    @Test
    void factoryNeverRunsAsleepAndKeepsItsSelectorChoice() {
        WorkGoalFactory f = WorkGoalFactory.of(EnumSet.of(CompanionMode.GUARD, CompanionMode.SLEEP), true,
                (companion, type) -> null);
        assertEquals(EnumSet.of(CompanionMode.GUARD), EnumSet.copyOf(f.modes()));
        assertTrue(f.target());
        assertFalse(factory(CompanionMode.MINE).target());
    }

    @Test
    void selectionFollowsEachFactorysModesInTypeOrder() {
        Identifier mine = id("select_mine");
        Identifier scout = id("select_scout");
        ensure(mine, CompanionMode.MINE);
        ensure(scout, CompanionMode.SCOUT, CompanionMode.FOLLOW);
        List<Identifier> typeGoals = List.of(scout, id("select_unregistered"), mine, scout);
        assertEquals(List.of(mine), WorkGoals.activeFor(typeGoals, CompanionMode.MINE));
        assertEquals(List.of(scout), WorkGoals.activeFor(typeGoals, CompanionMode.SCOUT));
        assertEquals(List.of(scout), WorkGoals.activeFor(typeGoals, CompanionMode.FOLLOW));
        assertEquals(List.of(), WorkGoals.activeFor(typeGoals, CompanionMode.STAY));
        assertEquals(List.of(), WorkGoals.activeFor(typeGoals, CompanionMode.SLEEP));
    }

    private static CompanionType parse(String json, List<ValidationIssue> issues) {
        return CompanionTypeParser.parse(id("bot_type"), JsonParser.parseString(json), LOOKUP, WorkGoals.known(),
                issues).orElseThrow();
    }

    @Test
    void parserAcceptsRegisteredWorkGoalsOnly() {
        Identifier carry = id("parse_carry");
        ensure(carry, CompanionMode.CARRY);
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = parse("{\"entity\":\"nerocompanion_test:bot\",\"work_goals\":[\"" + carry
                + "\",\"nerocompanion_test:parse_nobody\"]}", issues);
        assertEquals(List.of(carry), type.workGoals());
        assertEquals(1, issues.size(), issues::toString);
        assertEquals("work_goals", issues.get(0).field());
    }

    @Test
    void cargoSlotsDefaultToZeroAndAreRangeChecked() {
        List<ValidationIssue> issues = new ArrayList<>();
        assertEquals(0, parse("{\"entity\":\"nerocompanion_test:bot\"}", issues).cargoSlots());
        assertEquals(8, parse("{\"entity\":\"nerocompanion_test:bot\",\"cargo_slots\":8}", issues).cargoSlots());
        assertTrue(issues.isEmpty(), issues::toString);
        assertEquals(0, parse("{\"entity\":\"nerocompanion_test:bot\",\"cargo_slots\":99}", issues).cargoSlots());
        assertEquals(1, issues.size());
        assertEquals("cargo_slots", issues.get(0).field());
    }

    @Test
    void aChipBringsItsGoalsToAnyChassis() {
        Identifier guard = id("chip_guard");
        ensure(guard, CompanionMode.GUARD);
        Identifier chip = id("chip_guard_protocol");
        WorkGoals.grantByChip(chip, List.of(guard));
        List<Identifier> base = List.of(id("chip_other"));
        assertEquals(base, WorkGoals.withChipGoals(base, List.of()));
        List<Identifier> withChip = WorkGoals.withChipGoals(base, List.of(chip));
        assertEquals(List.of(id("chip_other"), guard), withChip);
        assertEquals(List.of(guard), WorkGoals.activeFor(withChip, CompanionMode.GUARD));
        assertEquals(withChip, WorkGoals.withChipGoals(withChip, List.of(chip)));
    }

    @Test
    void pickupRadiusDefaultsToEightAndIsRangeChecked() {
        List<ValidationIssue> issues = new ArrayList<>();
        assertEquals(8, parse("{\"entity\":\"nerocompanion_test:bot\"}", issues).pickupRadius());
        assertEquals(12, parse("{\"entity\":\"nerocompanion_test:bot\",\"pickup_radius\":12}", issues)
                .pickupRadius());
        assertTrue(issues.isEmpty(), issues::toString);
        assertEquals(8, parse("{\"entity\":\"nerocompanion_test:bot\",\"pickup_radius\":40}", issues)
                .pickupRadius());
        assertEquals("pickup_radius", issues.get(0).field());
    }

    @Test
    void entityBondLevelsComeFromTheBondTable() {
        for (int points : new int[] {0, 99, 100, 299, 300, 700, 1499, 1500, 999_999}) {
            assertEquals(za.co.neroland.nerocompanion.bond.BondLevels.level(points),
                    za.co.neroland.nerocompanion.entity.Bond.level(points));
            assertEquals(za.co.neroland.nerocompanion.bond.BondLevels.pointsToNext(points),
                    za.co.neroland.nerocompanion.entity.Bond.pointsToNext(points));
        }
    }
}
