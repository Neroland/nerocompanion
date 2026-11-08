package za.co.neroland.nerocompanion.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;

/**
 * The link sections are requester-scoped by owner UUID and carry no coordinates and no player or entity UUID
 * (decision 013, ecosystem convention #12).
 */
class CompanionLinkSnapshotsTest {

    private static final Pattern UUID_LIKE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final LinkFixtures.World world = new LinkFixtures.World();
    private final CompanionLinkSnapshots snapshots = new CompanionLinkSnapshots(() -> world);

    private CompanionRecord aliceDrone;
    private CompanionRecord aliceBot;
    private CompanionRecord aliceLost;
    private CompanionRecord bobBot;

    private void populate() {
        aliceDrone = world.deploy(LinkFixtures.record(alice, "personal_drone", CompanionMode.FOLLOW, "Scout",
                Optional.of(LinkFixtures.BAY_A)), new HomePos(LinkFixtures.OVERWORLD, new BlockPos(1240, 70, -5670)));
        aliceBot = world.park(LinkFixtures.record(alice, "mining_bot", CompanionMode.MINE, "Digger",
                Optional.of(LinkFixtures.BAY_A)), LinkFixtures.BAY_A);
        aliceLost = world.lose(LinkFixtures.record(alice, "scrapling", CompanionMode.FOLLOW, null,
                Optional.of(LinkFixtures.BAY_A)), new HomePos(LinkFixtures.NETHER, new BlockPos(555, 33, 777)));
        bobBot = world.park(LinkFixtures.record(bob, "carry_bot", CompanionMode.CARRY, "Bobs Bot",
                Optional.of(LinkFixtures.BAY_B)), LinkFixtures.BAY_B);
        world.online.put(alice, new HomePos(LinkFixtures.OVERWORLD, new BlockPos(1250, 70, -5660)));
        world.online.put(bob, new HomePos(LinkFixtures.OVERWORLD, new BlockPos(1245, 70, -5665)));
        world.salvage.put(bob, new CompanionLinkView.SalvageView(12, 1_789_000_600_000L));
    }

    private JsonObject read(UUID who, String section) {
        return snapshots.snapshot(who, section, Map.of());
    }

    private static Set<String> ids(JsonArray rows, String key) {
        Set<String> out = new HashSet<>();
        for (JsonElement row : rows) {
            out.add(row.getAsJsonObject().get(key).getAsString());
        }
        return out;
    }

    @Test
    void eachPlayerReadsOnlyTheirOwnCompanions() {
        populate();
        JsonArray aliceRows = read(alice, "roster").getAsJsonArray("companions");
        assertEquals(Set.of(aliceDrone.companionId().toString(), aliceBot.companionId().toString(),
                aliceLost.companionId().toString()), ids(aliceRows, "id"));
        JsonArray bobRows = read(bob, "roster").getAsJsonArray("companions");
        assertEquals(Set.of(bobBot.companionId().toString()), ids(bobRows, "id"));

        // Alice's sections name nothing of Bob's, and the other way round.
        for (String section : CompanionLinkModule.SECTIONS) {
            String a = read(alice, section).toString();
            String b = read(bob, section).toString();
            assertFalse(a.contains(bobBot.companionId().toString()) || a.contains("Bobs Bot"), section + ": " + a);
            assertFalse(b.contains(aliceDrone.companionId().toString()) || b.contains("Digger")
                    || b.contains("Scout"), section + ": " + b);
        }
        // Bob's salvage is his; Alice has none.
        assertFalse(read(alice, "salvage").get("held").getAsBoolean());
        assertEquals(12, read(bob, "salvage").get("count").getAsInt());
    }

    @Test
    void aStrangerReadsNothing() {
        populate();
        UUID carol = UUID.randomUUID();
        assertEquals(0, read(carol, "roster").getAsJsonArray("companions").size());
        assertEquals(0, read(carol, "bay").getAsJsonArray("bays").size());
        assertFalse(read(carol, "salvage").get("held").getAsBoolean());
        assertEquals(new JsonObject(), read(carol, "positions"));
    }

    @Test
    void noSectionCarriesACoordinateOrAPlayerOrEntityUuid() {
        populate();
        for (UUID who : List.of(alice, bob)) {
            Set<String> ownIds = new HashSet<>();
            for (var entry : world.book.entriesOf(who)) {
                ownIds.add(entry.companionId().toString());
            }
            for (String section : CompanionLinkModule.SECTIONS) {
                String json = read(who, section).toString();
                // Ids are random hex: mask them so a digit run inside one is not mistaken for a coordinate.
                String masked = UUID_LIKE.matcher(json).replaceAll("<id>");
                for (String coordinate : List.of("1234", "5678", "4321", "8765", "1240", "5670", "1250", "5660",
                        "555", "777", "\"x\"", "\"y\"", "\"z\"", "\"pos\"", "biome", "structure")) {
                    assertFalse(masked.contains(coordinate), section + " leaks " + coordinate + ": " + json);
                }
                assertFalse(json.contains(alice.toString()) || json.contains(bob.toString()), "owner id: " + json);
                for (UUID entity : world.entityIds) {
                    assertFalse(json.contains(entity.toString()), "entity id: " + json);
                }
                Matcher m = UUID_LIKE.matcher(json);
                while (m.find()) {
                    assertTrue(ownIds.contains(m.group()), "only own companion ids: " + m.group());
                }
            }
        }
    }

    @Test
    void rosterRowsCarryTheAppsFields() {
        populate();
        JsonObject roster = read(alice, "roster");
        assertEquals(1_789_000_000_000L, roster.get("asOf").getAsLong());
        JsonObject drone = null;
        JsonObject lost = null;
        JsonObject parked = null;
        for (JsonElement el : roster.getAsJsonArray("companions")) {
            JsonObject row = el.getAsJsonObject();
            String id = row.get("id").getAsString();
            if (id.equals(aliceDrone.companionId().toString())) {
                drone = row;
            } else if (id.equals(aliceLost.companionId().toString())) {
                lost = row;
            } else {
                parked = row;
            }
        }
        for (String key : List.of("id", "type", "chassis", "name", "skin", "hat", "mode", "supportedModes",
                "charge", "chargeMax", "dimension", "areaHint", "lost", "bond", "home", "parked")) {
            assertTrue(drone.has(key), key);
        }
        assertEquals("drone", drone.get("type").getAsString());
        assertEquals("nerocompanion:personal_drone", drone.get("chassis").getAsString());
        assertEquals("Scout", drone.get("name").getAsString());
        assertEquals("near_you", drone.get("areaHint").getAsString());
        assertEquals("Overworld", drone.get("dimension").getAsString());
        assertEquals(100, drone.get("chargeMax").getAsInt());
        assertEquals("bay", drone.get("home").getAsString());

        assertEquals("pet", lost.get("type").getAsString());
        assertTrue(lost.get("lost").getAsBoolean());
        assertEquals("unknown", lost.get("areaHint").getAsString());
        assertEquals("The Nether", lost.get("dimension").getAsString());
        assertEquals("Scrapling", lost.get("name").getAsString());

        assertEquals("bot", parked.get("type").getAsString());
        assertTrue(parked.get("parked").getAsBoolean());
        assertEquals("at_home", parked.get("areaHint").getAsString());
        assertEquals("mine", parked.get("mode").getAsString());
    }

    @Test
    void theAreaHintIsCoarse() {
        HomePos home = new HomePos(LinkFixtures.OVERWORLD, new BlockPos(0, 64, 0));
        HomePos nearHome = new HomePos(LinkFixtures.OVERWORLD, new BlockPos(5, 64, 5));
        HomePos far = new HomePos(LinkFixtures.OVERWORLD, new BlockPos(900, 64, 900));
        HomePos nether = new HomePos(LinkFixtures.NETHER, new BlockPos(5, 64, 5));
        assertEquals("at_home", CompanionLinkSnapshots.areaHint(State.LIVE, Optional.of(nearHome),
                Optional.of(home), Optional.empty()));
        assertEquals("near_you", CompanionLinkSnapshots.areaHint(State.LIVE, Optional.of(far), Optional.of(home),
                Optional.of(new HomePos(LinkFixtures.OVERWORLD, new BlockPos(920, 64, 900)))));
        assertEquals("away", CompanionLinkSnapshots.areaHint(State.LIVE, Optional.of(far), Optional.of(home),
                Optional.of(nearHome)));
        assertEquals("away", CompanionLinkSnapshots.areaHint(State.LIVE, Optional.of(nether), Optional.of(home),
                Optional.of(nearHome)));
        assertEquals("unknown", CompanionLinkSnapshots.areaHint(State.LIVE, Optional.empty(), Optional.of(home),
                Optional.empty()));
        assertEquals("unknown", CompanionLinkSnapshots.areaHint(State.LOST, Optional.of(nearHome),
                Optional.of(home), Optional.of(nearHome)));
        assertEquals("unknown", CompanionLinkSnapshots.areaHint(State.CAPSULE, Optional.of(nearHome),
                Optional.of(home), Optional.of(nearHome)));
        assertEquals("at_home", CompanionLinkSnapshots.areaHint(State.PARKED, Optional.empty(), Optional.empty(),
                Optional.empty()));
    }

    @Test
    void baysAreNumberedAndListOnlyOwnParkedCompanions() {
        populate();
        // Bob parks a second bot in Alice's Bay: Alice must not see it in her slots.
        CompanionRecord intruder = world.park(LinkFixtures.record(bob, "repair_bot", CompanionMode.REPAIR, "Patch",
                Optional.of(LinkFixtures.BAY_A)), LinkFixtures.BAY_A);
        JsonArray bays = read(alice, "bay").getAsJsonArray("bays");
        assertEquals(1, bays.size());
        JsonObject bay = bays.get(0).getAsJsonObject();
        assertEquals(1, bay.get("index").getAsInt());
        assertEquals(Set.of(aliceBot.companionId().toString()), ids(bay.getAsJsonArray("slots"), "companionId"));
        assertFalse(bays.toString().contains(intruder.companionId().toString()));

        JsonArray bobBays = read(bob, "bay").getAsJsonArray("bays");
        assertEquals(2, bobBays.size());
        assertEquals(1, bobBays.get(0).getAsJsonObject().get("index").getAsInt());
        assertEquals(2, bobBays.get(1).getAsJsonObject().get("index").getAsInt());
    }

    @Test
    void aLoadedCompanionListsItsChipUnlockedModes() {
        populate();
        HomePos at = new HomePos(LinkFixtures.OVERWORLD, new BlockPos(1240, 70, -5670));
        world.live.put(aliceDrone.companionId(), new CompanionLinkView.Live(CompanionMode.SCOUT, 80, at,
                Optional.of("Scout"), 420, Optional.empty(), Optional.empty(), Optional.of(LinkFixtures.BAY_A),
                List.of(CompanionMode.FOLLOW, CompanionMode.STAY, CompanionMode.GUARD, CompanionMode.SCOUT,
                        CompanionMode.SLEEP)));
        for (JsonElement el : read(alice, "roster").getAsJsonArray("companions")) {
            JsonObject row = el.getAsJsonObject();
            if (row.get("id").getAsString().equals(aliceDrone.companionId().toString())) {
                assertTrue(row.getAsJsonArray("supportedModes").toString().contains("\"guard\""), row.toString());
                assertEquals("scout", row.get("mode").getAsString());
                assertEquals(80, row.get("charge").getAsInt());
                return;
            }
        }
        throw new AssertionError("drone row missing");
    }

    @Test
    void dimensionNamesAreDisplayNames() {
        assertEquals("Overworld", CompanionLinkSnapshots.dimensionName("minecraft:overworld"));
        assertEquals("The End", CompanionLinkSnapshots.dimensionName("minecraft:the_end"));
        assertEquals("Mars Orbit", CompanionLinkSnapshots.dimensionName("nerospace:mars_orbit"));
        assertEquals("Unknown", CompanionLinkSnapshots.dimensionName(""));
    }

    @Test
    void noServerMeansEmptySections() {
        CompanionLinkSnapshots offline = new CompanionLinkSnapshots(() -> null);
        assertEquals(0, offline.snapshot(alice, "roster", Map.of()).getAsJsonArray("companions").size());
        assertFalse(offline.snapshot(alice, "salvage", Map.of()).get("held").getAsBoolean());
    }
}
