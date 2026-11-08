package za.co.neroland.nerocompanion.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.pet.PetWork;
import za.co.neroland.nerocompanion.ai.pet.PlayRules;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.type.CompanionTypeParser;
import za.co.neroland.nerocompanion.entity.type.ValidationIssue;

/** Comfort, never combat: the Scrapling's data and goals give it no attack and no stat chip. */
class ScraplingRulesTest {

    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("nerocompanion", "scrapling");
    private static final String GOAL_PKG = "za.co.neroland.nerocompanion.ai.pet.";

    private static CompanionType scrapling(List<ValidationIssue> issues) throws Exception {
        try (InputStream in = ScraplingRulesTest.class.getResourceAsStream(
                "/data/nerocompanion/companion_types/scrapling.json")) {
            assertNotNull(in, "scrapling.json is on the classpath");
            Set<Identifier> known = PetWork.SPECS.stream().map(PetWork.Spec::id).collect(Collectors.toSet());
            return CompanionTypeParser.parse(TYPE, JsonParser.parseReader(new InputStreamReader(in,
                    StandardCharsets.UTF_8)), entity -> entity.equals(TYPE) ? Optional.of(Locomotion.GROUND)
                    : Optional.empty(), known, issues).orElseThrow();
        }
    }

    @Test
    void typeFileIsAHarmlessSmallPetWithOneCosmeticSlot() throws Exception {
        List<ValidationIssue> issues = new ArrayList<>();
        CompanionType type = scrapling(issues);
        assertTrue(issues.isEmpty(), issues::toString);
        assertEquals(0.0D, type.attackDamage());
        assertTrue(type.small(), "perches");
        assertEquals(1, type.chipSlots());
        assertEquals(Set.of(ChipCategory.COSMETIC), type.chipCategories(), "no stat or capability chips");
        assertEquals(EnumSet.of(CompanionMode.FOLLOW, CompanionMode.STAY, CompanionMode.SLEEP),
                EnumSet.copyOf(type.modes()), "no GUARD or other work mode");
        assertEquals(0, type.cargoSlots());
        assertEquals(6000L, type.energy().capacity());
        assertEquals(PetWork.SPECS.stream().map(PetWork.Spec::id).collect(Collectors.toSet()),
                Set.copyOf(type.workGoals()));
    }

    @Test
    void noPetGoalIsATargetOrAttackGoal() throws Exception {
        for (PetWork.Spec spec : PetWork.SPECS) {
            assertFalse(spec.target(), spec.id() + " must not be a target goal");
            assertFalse(spec.modes().contains(CompanionMode.GUARD), spec.id() + " runs in GUARD");
        }
        ClassLoader loader = ScraplingRulesTest.class.getClassLoader();
        Class<?> target = Class.forName("net.minecraft.world.entity.ai.goal.target.TargetGoal", false, loader);
        Class<?> melee = Class.forName("net.minecraft.world.entity.ai.goal.MeleeAttackGoal", false, loader);
        for (String name : List.of("PetFetchGoal", "PetPlayGoal", "PetNapGoal")) {
            Class<?> goal = Class.forName(GOAL_PKG + name, false, loader);
            assertFalse(target.isAssignableFrom(goal), name);
            assertFalse(melee.isAssignableFrom(goal), name);
        }
    }

    @Test
    void entityRefusesTargetsAndAttacks() throws Exception {
        ClassLoader loader = ScraplingRulesTest.class.getClassLoader();
        Class<?> entity = Class.forName("za.co.neroland.nerocompanion.entity.types.ScraplingEntity", false, loader);
        Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity", false, loader);
        Method canAttack = entity.getDeclaredMethod("canAttack", living);
        Method setTarget = entity.getDeclaredMethod("setTarget", living);
        assertEquals(entity, canAttack.getDeclaringClass());
        assertEquals(entity, setTarget.getDeclaringClass());
    }

    @Test
    void aStayingPetNeverRunsOff() {
        for (int roll = 0; roll < 100; roll++) {
            assertTrue(PlayRules.pick(roll, true) != PlayRules.Kind.ZOOMIES);
        }
        assertEquals(PlayRules.Kind.ZOOMIES, PlayRules.pick(0, false));
        assertEquals(PlayRules.Kind.POUNCE, PlayRules.pick(99, false));
    }
}
