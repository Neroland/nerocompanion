package za.co.neroland.nerocompanion.chip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.upgrade.UpgradeType;

import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;

/** The chip rule book: slot, tier, category, duplicate and exclusive validation, plus the registry's shape. */
class ChipValidationTest {

    private static final ChipValidation.Chassis BOT = new ChipValidation.Chassis(3, EnumSet.allOf(ChipCategory.class));
    private static final ChipValidation.Chassis DRONE = new ChipValidation.Chassis(2, EnumSet.allOf(ChipCategory.class));
    private static final ChipValidation.Chassis SCRAPLING = new ChipValidation.Chassis(1, EnumSet.of(ChipCategory.COSMETIC));
    private static final ChipRules.Exclusions RULES = ChipRules.DEFAULT;

    private static ChipStack stack(ChipKind kind, int tier) {
        return new ChipStack(kind.id(), tier);
    }

    private static ChipValidation.Outcome insert(List<ChipStack> installed, ChipValidation.Chassis chassis, int slot,
            ChipKind kind, int tier) {
        return ChipValidation.insert(installed, chassis, slot, kind, tier, RULES).outcome();
    }

    @Test
    void registryHasEighteenKindsAndThirtyItems() {
        assertEquals(18, ChipKinds.all().size());
        assertEquals(30, ChipKinds.itemCount());
        long stat = ChipKinds.all().stream().filter(k -> k.category() == ChipCategory.STAT).count();
        long capability = ChipKinds.all().stream().filter(k -> k.category() == ChipCategory.CAPABILITY).count();
        long cosmetic = ChipKinds.all().stream().filter(k -> k.category() == ChipCategory.COSMETIC).count();
        assertEquals(6, stat);
        assertEquals(8, capability);
        assertEquals(4, cosmetic);
        Set<String> items = new HashSet<>();
        for (ChipKind kind : ChipKinds.all()) {
            for (int tier = 1; tier <= kind.maxTier(); tier++) {
                assertTrue(items.add(kind.itemName(tier)), "duplicate item name " + kind.itemName(tier));
            }
        }
    }

    @Test
    void onlyTheFourCoreFamiliesMapOntoCoreUpgradeTypes() {
        assertEquals(UpgradeType.SPEED, ChipKinds.SPEED.coreType().orElseThrow());
        assertEquals(UpgradeType.EFFICIENCY, ChipKinds.EFFICIENCY.coreType().orElseThrow());
        assertEquals(UpgradeType.RANGE, ChipKinds.RANGE.coreType().orElseThrow());
        assertEquals(UpgradeType.CAPACITY, ChipKinds.CAPACITY.coreType().orElseThrow());
        long mapped = ChipKinds.all().stream().filter(k -> k.coreType().isPresent()).count();
        assertEquals(4, mapped);
    }

    @Test
    void acceptsAValidInsertIntoTheFirstFreeOrAGivenSlot() {
        assertEquals(ChipValidation.Outcome.OK, insert(List.of(), BOT, -1, ChipKinds.SPEED, 2));
        assertEquals(ChipValidation.Outcome.OK, insert(List.of(stack(ChipKinds.LAMP, 1)), BOT, 1, ChipKinds.SPEED, 3));
        assertEquals(ChipValidation.Outcome.OK, insert(List.of(), SCRAPLING, 0, ChipKinds.HUSH, 1));
    }

    @Test
    void rejectsANonChipABadSlotAndAFullChassis() {
        assertEquals(ChipValidation.Outcome.NOT_A_CHIP, insert(List.of(), BOT, 0, null, 1));
        assertEquals(ChipValidation.Outcome.BAD_SLOT, insert(List.of(), DRONE, 2, ChipKinds.SPEED, 1));
        assertEquals(ChipValidation.Outcome.BAD_SLOT, insert(List.of(), DRONE, -2, ChipKinds.SPEED, 1));
        List<ChipStack> full = List.of(stack(ChipKinds.SPEED, 1), stack(ChipKinds.LAMP, 1));
        assertEquals(ChipValidation.Outcome.FULL, insert(full, DRONE, -1, ChipKinds.HUSH, 1));
    }

    @Test
    void rejectsTheWrongCategory() {
        assertEquals(ChipValidation.Outcome.WRONG_CATEGORY, insert(List.of(), SCRAPLING, 0, ChipKinds.SPEED, 1));
        assertEquals(ChipValidation.Outcome.WRONG_CATEGORY, insert(List.of(), SCRAPLING, 0, ChipKinds.LAMP, 1));
    }

    @Test
    void rejectsADuplicateKindAtAnyTier() {
        List<ChipStack> installed = List.of(stack(ChipKinds.SPEED, 1));
        assertEquals(ChipValidation.Outcome.DUPLICATE, insert(installed, BOT, -1, ChipKinds.SPEED, 1));
        assertEquals(ChipValidation.Outcome.DUPLICATE, insert(installed, BOT, -1, ChipKinds.SPEED, 3));
    }

    @Test
    void rejectsAnExclusivePairEitherWayAndNamesTheClash() {
        ChipValidation.Result result = ChipValidation.insert(List.of(stack(ChipKinds.LAMP, 1)), BOT, -1,
                ChipKinds.SILENT_RUNNING, 1, RULES);
        assertEquals(ChipValidation.Outcome.EXCLUSIVE, result.outcome());
        assertEquals(ChipKinds.LAMP.id(), result.clash().orElseThrow());
        assertEquals(ChipValidation.Outcome.EXCLUSIVE,
                insert(List.of(stack(ChipKinds.SILENT_RUNNING, 1)), BOT, -1, ChipKinds.LAMP, 1));
        assertEquals(ChipValidation.Outcome.EXCLUSIVE,
                insert(List.of(stack(ChipKinds.ARMOUR, 2)), BOT, -1, ChipKinds.SPEED, 1));
        assertEquals(ChipValidation.Outcome.EXCLUSIVE,
                insert(List.of(stack(ChipKinds.HUSH, 1)), BOT, -1, ChipKinds.CHATTERBOX, 1));
    }

    @Test
    void rejectsATierOutsideTheKindsRange() {
        assertEquals(ChipValidation.Outcome.BAD_TIER, insert(List.of(), BOT, -1, ChipKinds.SPEED, 4));
        assertEquals(ChipValidation.Outcome.BAD_TIER, insert(List.of(), BOT, -1, ChipKinds.SPEED, 0));
        assertEquals(ChipValidation.Outcome.BAD_TIER, insert(List.of(), BOT, -1, ChipKinds.LAMP, 2));
    }

    @Test
    void removeNeedsAFilledSlotAndCompactsTheRest() {
        List<ChipStack> installed = List.of(stack(ChipKinds.SPEED, 1), stack(ChipKinds.LAMP, 1), stack(ChipKinds.HUSH, 1));
        assertTrue(ChipValidation.remove(installed, 0).ok());
        assertEquals(ChipValidation.Outcome.SLOT_EMPTY, ChipValidation.remove(installed, 3).outcome());
        assertEquals(ChipValidation.Outcome.SLOT_EMPTY, ChipValidation.remove(List.of(), 0).outcome());
        List<ChipStack> after = ChipValidation.withRemoved(installed, 0);
        assertEquals(List.of(stack(ChipKinds.LAMP, 1), stack(ChipKinds.HUSH, 1)), after);
        assertEquals(3, ChipValidation.withInserted(after, stack(ChipKinds.RANGE, 2)).size());
    }

    @Test
    void sanitizeKeepsValidChipsInOrderAndReturnsTheRestNeverDroppingAny() {
        List<ChipStack> chips = new ArrayList<>(List.of(stack(ChipKinds.HUSH, 1), stack(ChipKinds.SPEED, 1),
                new ChipStack(Identifier.fromNamespaceAndPath("nerocompanion", "nope"), 1),
                stack(ChipKinds.CHATTERBOX, 1)));
        ChipValidation.Split split = ChipValidation.sanitize(chips, SCRAPLING, RULES, ChipKinds::byId);
        assertEquals(List.of(stack(ChipKinds.HUSH, 1)), split.kept());
        assertEquals(3, split.rejected().size());
        assertEquals(chips.size(), split.kept().size() + split.rejected().size());
    }

    @Test
    void exclusionsParseFromDataAndIgnoreUnknownOrMalformedPairs() {
        Set<String> pairs = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        ChipRules.parse(JsonParser.parseString("{\"pairs\": [[\"nerocompanion:lamp\", \"nerocompanion:hush\"],"
                + " [\"nerocompanion:lamp\"], [\"nerocompanion:lamp\", \"other:thing\"]]}"), pairs, warnings::add);
        ChipRules.Exclusions exclusions = new ChipRules.Exclusions(pairs);
        assertTrue(exclusions.excludes(ChipKinds.HUSH.id(), ChipKinds.LAMP.id()));
        assertFalse(exclusions.excludes(ChipKinds.LAMP.id(), ChipKinds.SILENT_RUNNING.id()));
        assertEquals(2, warnings.size());
        List<String> bad = new ArrayList<>();
        ChipRules.parse(JsonParser.parseString("[]"), new HashSet<>(), bad::add);
        assertEquals(1, bad.size());
    }

    @Test
    void effectsFollowTheEnergyBalance() {
        assertEquals(1.0D, ChipEffects.drainMultiplier(List.of()), 1e-9);
        assertEquals(85L, ChipEffects.drain(100L, List.of(stack(ChipKinds.EFFICIENCY, 1))));
        assertEquals(75L, ChipEffects.drain(100L, List.of(stack(ChipKinds.EFFICIENCY, 2))));
        assertEquals(65L, ChipEffects.drain(100L, List.of(stack(ChipKinds.EFFICIENCY, 3))));
        assertEquals(30_000L, ChipEffects.capacity(24_000L, List.of(stack(ChipKinds.CAPACITY, 1))));
        assertEquals(36_000L, ChipEffects.capacity(24_000L, List.of(stack(ChipKinds.CAPACITY, 2))));
        assertEquals(42_000L, ChipEffects.capacity(24_000L, List.of(stack(ChipKinds.CAPACITY, 3))));
        assertEquals(1.2D, ChipEffects.speedMultiplier(List.of(stack(ChipKinds.SPEED, 2))), 1e-9);
        assertEquals(6, ChipEffects.rangeBonus(List.of(stack(ChipKinds.RANGE, 3))));
        assertTrue(ChipEffects.unlockedModes(List.of(stack(ChipKinds.GUARD_PROTOCOL, 1))).contains(CompanionMode.GUARD));
        assertEquals(ChipKinds.GUARD_PROTOCOL, ChipEffects.unlockerOf(CompanionMode.GUARD).orElseThrow());
        assertTrue(ChipEffects.unlockerOf(CompanionMode.MINE).isEmpty());
    }
}
