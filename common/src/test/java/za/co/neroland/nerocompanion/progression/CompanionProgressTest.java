package za.co.neroland.nerocompanion.progression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.type.ValidationIssue;
import za.co.neroland.nerocompanion.look.Look;
import za.co.neroland.nerocompanion.look.LookKind;
import za.co.neroland.nerocompanion.look.LookParser;
import za.co.neroland.nerocompanion.look.LookUnlocks;
import za.co.neroland.nerocompanion.look.Looks;

/**
 * The four Stage 6 gates and the ten quest milestones fire from the events that earn them, credited to the stored
 * owner UUID, and the {@code skin/<id>} hook a quest reward opens is exactly what unlocks the skin.
 */
class CompanionProgressTest {

    private static final UUID OWNER = new UUID(0L, 7L);

    private final List<Identifier> opened = new ArrayList<>();
    private final List<UUID> credited = new ArrayList<>();

    @BeforeEach
    void capture() {
        CompanionProgress.useSink((server, owner, gate) -> {
            this.credited.add(owner);
            this.opened.add(gate);
        });
    }

    @AfterEach
    void restore() {
        CompanionProgress.useSink(null);
    }

    @Test
    void deployOpensFirstDeployAndFullSquadOnlyAtTheCap() {
        assertEquals(List.of(CompanionMilestones.FIRST_DEPLOY), CompanionProgress.onDeploy(1, 4));
        assertEquals(List.of(CompanionMilestones.FIRST_DEPLOY), CompanionProgress.onDeploy(3, 4));
        assertEquals(List.of(CompanionMilestones.FIRST_DEPLOY, CompanionMilestones.FULL_SQUAD),
                CompanionProgress.onDeploy(4, 4));
        assertEquals(List.of(CompanionMilestones.FIRST_DEPLOY), CompanionProgress.onDeploy(9, 0), "no cap, no squad");
        CompanionProgress.deployed(null, OWNER, 4, 4);
        assertEquals(List.of(CompanionMilestones.FIRST_DEPLOY, CompanionMilestones.FULL_SQUAD), this.opened);
        assertEquals(List.of(OWNER, OWNER), this.credited, "credited to the stored owner, nobody else");
    }

    @Test
    void everyEventOpensItsGate() {
        CompanionProgress.chipInstalled(null, OWNER);
        CompanionProgress.charged(null, OWNER);
        for (CompanionProgress.Work work : CompanionProgress.Work.values()) {
            CompanionProgress.workDone(null, OWNER, work);
        }
        CompanionProgress.bondLevelUp(null, OWNER, 2, 3);
        CompanionProgress.hatched(null, OWNER);
        CompanionProgress.recovered(null, OWNER);
        CompanionProgress.deployed(null, OWNER, 4, 4);
        assertEquals(Set.copyOf(CompanionMilestones.ALL), Set.copyOf(this.opened), "every shipped gate has an event");
    }

    @Test
    void workMapsOneToOne() {
        Set<Identifier> gates = new java.util.HashSet<>();
        for (CompanionProgress.Work work : EnumSet.allOf(CompanionProgress.Work.class)) {
            gates.add(CompanionProgress.forWork(work));
        }
        assertEquals(Set.of(CompanionMilestones.FIRST_MINE, CompanionMilestones.FIRST_CARRY,
                CompanionMilestones.FIRST_REPAIR, CompanionMilestones.FIRST_GUARD), gates);
    }

    @Test
    void bondThreeOnlyWhenTheLevelReachesThree() {
        assertFalse(CompanionProgress.bondMilestone(1, 2));
        assertTrue(CompanionProgress.bondMilestone(2, 3));
        assertTrue(CompanionProgress.bondMilestone(1, 4), "a jump past 3 counts");
        assertFalse(CompanionProgress.bondMilestone(3, 3));
        CompanionProgress.bondLevelUp(null, OWNER, 0, 1);
        assertTrue(this.opened.isEmpty());
        CompanionProgress.bondLevelUp(null, OWNER, 3, 4);
        assertEquals(List.of(CompanionMilestones.BOND_THREE), this.opened);
    }

    @Test
    void hatchIsAlsoAnAdoption() {
        CompanionProgress.hatched(null, OWNER);
        assertEquals(List.of(CompanionMilestones.FIRST_HATCH, CompanionMilestones.PET_ADOPTED), this.opened);
        this.opened.clear();
        CompanionProgress.petAdopted(null, OWNER);
        assertEquals(List.of(CompanionMilestones.PET_ADOPTED), this.opened);
    }

    @Test
    void noOwnerNoMilestoneAndAFailingStoreNeverBreaksGameplay() {
        CompanionProgress.chipInstalled(null, null);
        assertTrue(this.opened.isEmpty());
        CompanionProgress.useSink((server, owner, gate) -> {
            throw new IllegalStateException("store down");
        });
        assertDoesNotThrow(() -> CompanionProgress.workDone(null, OWNER, CompanionProgress.Work.MINE));
    }

    /**
     * End to end on the shipped data: the quest that pays out {@code mod_hook nerocompanion:hook/skin/aurora}, the hook
     * gate QuestHooks reads as skin {@code aurora}, and the shipped Aurora skin whose {@code hook} rule unlocks exactly
     * when that gate is open - for every companion type.
     */
    @Test
    void theSkinHookAQuestPaysIsTheSkinsUnlock() throws IOException, URISyntaxException {
        Path root = QuestPackTest.resourcesRoot();
        Identifier hook = null;
        Path quests = root.resolve("data/nerocompanion/neroquests/quests");
        for (Path file : (Iterable<Path>) Files.walk(quests).filter(p -> p.toString().endsWith(".json"))::iterator) {
            JsonObject quest = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (quest.has("rewards")) {
                for (JsonElement e : quest.getAsJsonArray("rewards")) {
                    JsonObject r = e.getAsJsonObject();
                    if (r.get("type").getAsString().endsWith("mod_hook")) {
                        hook = Identifier.tryParse(r.get("gate").getAsString());
                    }
                }
            }
        }
        assertEquals(QuestHooks.skinHook("aurora"), hook, "a quest pays the Aurora hook");
        assertEquals("aurora", QuestHooks.skinOf(hook));

        Identifier drone = Identifier.fromNamespaceAndPath("nerocompanion", "personal_drone");
        Identifier miner = Identifier.fromNamespaceAndPath("nerocompanion", "mining_bot");
        List<ValidationIssue> issues = new ArrayList<>();
        Look aurora = LookParser.parse(Identifier.fromNamespaceAndPath("nerocompanion", "aurora"), LookKind.SKIN,
                JsonParser.parseString(Files.readString(root.resolve("data/nerocompanion/skins/aurora.json"),
                        StandardCharsets.UTF_8)), Set.of(drone, miner), Looks.MODEL_OVERRIDES, issues).orElseThrow();
        assertTrue(issues.isEmpty(), issues::toString);
        Set<Identifier> openGates = new java.util.HashSet<>();
        LookUnlocks.Context context = new LookUnlocks.Context() {
            @Override
            public int bondLevel() {
                return 0;
            }

            @Override
            public boolean advancementDone(Identifier id) {
                return false;
            }

            @Override
            public boolean hasItem(Identifier id) {
                return false;
            }

            @Override
            public String season() {
                return "none";
            }

            @Override
            public boolean hookOpen(Identifier id) {
                // LookService answers a skin hook with QuestHooks.isSkinUnlocked, i.e. "is the hook gate open".
                String skin = QuestHooks.skinOf(id);
                return skin != null && openGates.contains(QuestHooks.skinHook(skin));
            }
        };
        assertFalse(LookUnlocks.unlocked(aurora, drone, context), "locked before the quest pays out");
        openGates.add(hook); // the quest reward opens the hook gate
        assertTrue(LookUnlocks.unlocked(aurora, drone, context));
        assertTrue(LookUnlocks.unlocked(aurora, miner, context), "for every type the skin allows");
    }
}
