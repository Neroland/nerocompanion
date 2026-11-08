package za.co.neroland.nerocompanion.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.type.ValidationIssue;
import za.co.neroland.nerocompanion.look.Look;
import za.co.neroland.nerocompanion.look.LookKind;
import za.co.neroland.nerocompanion.look.LookParser;
import za.co.neroland.nerocompanion.look.Looks;
import za.co.neroland.nerocompanion.look.UnlockRule;

/** The gallery plan holds every exhibit, each in its own slot inside a stage sized to fit them. */
class GalleryLayoutTest {

    private static final List<Identifier> TYPES = List.of(id("personal_drone"), id("scout_drone"), id("mining_bot"),
            id("carry_bot"), id("repair_bot"), id("combat_bot"), id("scrapling"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion", path);
    }

    /** {@code common/src/main/resources}, found by walking up from the working directory. */
    private static Path resources() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("common/src/main/resources");
            if (Files.isDirectory(candidate.resolve("data/nerocompanion"))) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Look> looks(Path resources, LookKind kind) throws IOException {
        List<Look> out = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        Path dir = resources.resolve("data/nerocompanion").resolve(kind.directory());
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String relative = dir.relativize(file).toString().replace('\\', '/');
                Identifier lookId = id(relative.substring(0, relative.length() - ".json".length()));
                LookParser.parse(lookId, kind, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)),
                        new HashSet<>(TYPES), Looks.MODEL_OVERRIDES, issues).ifPresent(out::add);
            }
        }
        assertEquals(List.of(), issues);
        return out;
    }

    /** Every shipped item, by its item model definition ({@code assets/nerocompanion/items/<id>.json}). */
    private static List<Identifier> items(Path resources) throws IOException {
        try (Stream<Path> files = Files.list(resources.resolve("assets/nerocompanion/items"))) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted()
                    .map(n -> id(n.substring(0, n.length() - ".json".length()))).toList();
        }
    }

    @Test
    void shippedContentAllGetsASlotInsideTheStage() throws IOException {
        Path resources = resources();
        Assumptions.assumeTrue(resources != null, "source tree not reachable from the test working directory");
        List<Look> skins = looks(resources, LookKind.SKIN);
        List<Look> hats = looks(resources, LookKind.HAT);
        List<Identifier> items = items(resources);
        GalleryLayout.Plan plan = GalleryLayout.plan(GalleryLayout.displays(TYPES, skins, hats),
                GalleryLayout.wallItems(items, TYPES));

        // Every type in its standard look.
        assertEquals(TYPES.size(), plan.count(GalleryLayout.STANDARD));
        // Every type skin on every type it allows (locked, seasonal and shop ones included), and nothing else.
        Set<String> skinPairs = new HashSet<>();
        for (Look skin : skins) {
            for (Identifier type : TYPES) {
                if (!skin.family() && !skin.isDefault() && skin.allows(type)) {
                    skinPairs.add(type + "|" + skin.id());
                }
            }
        }
        assertEquals(skinPairs, pairs(plan, GalleryLayout.SKINS, false));
        assertEquals(9, skinPairs.size(), "the nine shipped type skins are all there");
        // The family skin on every type.
        long families = skins.stream().filter(Look::family).count();
        assertTrue(families >= 1);
        assertEquals(families * TYPES.size(), plan.count(GalleryLayout.FAMILY));
        // Every hat on every type that may wear it - all of them, the tenth hat included.
        Set<String> hatPairs = new HashSet<>();
        for (Look hat : hats) {
            for (Identifier type : TYPES) {
                if (hat.allows(type)) {
                    hatPairs.add(type + "|" + hat.id());
                }
            }
        }
        assertEquals(hatPairs, pairs(plan, GalleryLayout.HATS, true));
        assertEquals(hatPairs.size(), plan.count(GalleryLayout.HATS));
        assertEquals(10, hats.size());
        // Poses: asleep per type + the Scrapling nap; work per animated type + charging; energy bands + bond levels.
        assertEquals(TYPES.size() + 1, plan.count(GalleryLayout.RESTING));
        assertEquals(GalleryLayout.WORK_POSES.size() + 1, plan.count(GalleryLayout.WORKING));
        assertEquals(GalleryLayout.ENERGY_LEVELS.length + GalleryLayout.BOND_LEVELS,
                plan.count(GalleryLayout.STATUS));
        assertEquals(1, plan.companions().stream().filter(s -> s.display().onPad()).count());
        assertTrue(plan.companions().stream().noneMatch(
                s -> (s.display().pose().flags() & za.co.neroland.nerocompanion.entity.CompanionEntity.FLAG_PERCHED)
                        != 0), "perch needs a player and is never staged");

        // The wall: every item, a filled and a blueprint Capsule per type, every egg stage.
        assertTrue(items.size() >= 50, "every shipped item: " + items.size());
        assertEquals(items.size(), plan.wallCount(GalleryLayout.WALL_ITEMS));
        Set<Identifier> walled = new HashSet<>();
        plan.wall().forEach(slot -> {
            if (slot.item().item() != null) {
                walled.add(slot.item().item());
            }
        });
        assertEquals(new HashSet<>(items), walled);
        assertEquals(TYPES.size(), plan.wallCount(GalleryLayout.WALL_FILLED));
        assertEquals(TYPES.size(), plan.wallCount(GalleryLayout.WALL_BLUEPRINTS));
        assertEquals(GalleryLayout.EGG_STAGES, plan.wallCount(GalleryLayout.WALL_EGGS));

        assertInside(plan);
    }

    @Test
    void aLargeCatalogueGrowsTheStageInsteadOfDroppingAnything() {
        List<Look> hats = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            hats.add(look(LookKind.HAT, "hat_" + i, List.of(Look.ANY_TYPE)));
        }
        List<Look> skins = new ArrayList<>();
        for (Identifier type : TYPES) {
            for (int i = 0; i < 20; i++) {
                skins.add(look(LookKind.SKIN, type.getPath() + "/s" + i, List.of(type.toString())));
            }
        }
        List<Identifier> items = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            items.add(id("item_" + i));
        }
        List<GalleryLayout.Display> displays = GalleryLayout.displays(TYPES, skins, hats);
        List<GalleryLayout.WallItem> wall = GalleryLayout.wallItems(items, TYPES);
        GalleryLayout.Plan plan = GalleryLayout.plan(displays, wall);
        assertEquals(displays.size(), plan.companionCount());
        assertEquals(40 * TYPES.size(), plan.count(GalleryLayout.HATS));
        assertEquals(20 * TYPES.size(), plan.count(GalleryLayout.SKINS));
        assertEquals(wall.size(), plan.wall().size());
        assertInside(plan);
    }

    @Test
    void anEmptyCatalogueStillFitsTheChargers() {
        GalleryLayout.Plan plan = GalleryLayout.plan(List.of(), List.of());
        assertEquals(0, plan.companionCount());
        assertInside(plan);
    }

    private static Look look(LookKind kind, String path, List<String> types) {
        return new Look(id(path), kind, types, "nerocompanion:entity/{type}/x", "", -1, -1, 0, 1, "", "",
                new UnlockRule(UnlockRule.Kind.HOOK, "nerocompanion:hook/test"));
    }

    private static Set<String> pairs(GalleryLayout.Plan plan, String section, boolean hat) {
        Set<String> out = new HashSet<>();
        for (GalleryLayout.Slot slot : plan.companions()) {
            if (slot.display().section().equals(section)) {
                Optional<Identifier> look = hat ? slot.display().hat() : slot.display().skin();
                assertTrue(look.isPresent());
                assertTrue(out.add(slot.display().type() + "|" + look.get()), "shown once: " + look.get());
            }
        }
        return out;
    }

    /** No slot outside the stage or on its edge, no two exhibits in one place, the wall at the back. */
    private static void assertInside(GalleryLayout.Plan plan) {
        assertEquals(plan.depth() - 2, plan.wallZ(), "the frames hang on the back wall");
        Set<Long> used = new HashSet<>();
        for (int[] charger : plan.chargers()) {
            assertTrue(charger[0] >= 1 && charger[0] <= plan.length() - 2);
            assertTrue(charger[1] >= 1 && charger[1] < plan.wallZ());
            assertTrue(used.add(key(charger[0], 0, charger[1])));
        }
        for (GalleryLayout.Slot slot : plan.companions()) {
            assertTrue(slot.dx() >= 1 && slot.dx() <= plan.length() - 2, "x inside: " + slot);
            assertTrue(slot.dz() >= 1 && slot.dz() < plan.wallZ(), "z in front of the wall: " + slot);
            assertTrue(used.add(key(slot.dx(), 0, slot.dz())), "one per slot: " + slot);
        }
        for (GalleryLayout.WallSlot slot : plan.wall()) {
            assertTrue(slot.dx() >= 1 && slot.dx() <= plan.length() - 2, "wall x inside: " + slot);
            assertTrue(slot.dy() >= 1 && slot.dy() < plan.height(), "wall y inside: " + slot);
            assertTrue(used.add(key(slot.dx(), slot.dy(), plan.wallZ())), "one per frame: " + slot);
        }
        for (GalleryLayout.Label label : plan.labels()) {
            assertTrue(label.dx() >= 1 && label.dx() + label.columns() - 1 <= plan.length() - 2, "label: " + label);
            assertTrue(used.add(key(label.dx(), 0, plan.wallZ())), "label under its group: " + label);
        }
        for (GalleryLayout.Row row : plan.rows()) {
            assertTrue(row.count() >= 1 && row.count() <= GalleryLayout.MAX_COLUMNS);
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) | ((long) y << 20) | z;
    }
}
