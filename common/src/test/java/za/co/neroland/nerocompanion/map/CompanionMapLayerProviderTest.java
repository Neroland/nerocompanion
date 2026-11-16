package za.co.neroland.nerocompanion.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.link.CompanionLinkModule;
import za.co.neroland.nerocompanion.link.CompanionLinkSnapshots;
import za.co.neroland.nerocompanion.link.CompanionLinkView;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * The companion layers show the viewer's own companions and Bays and nothing else: Core's conformance fixture
 * (two viewers, a third owner, an absent dimension, far bounds), the per-state rules, and the map-versus-link parity test
 * against the link module's {@code roster} and {@code bay} sections for owner, stranger, third-party and
 * erased-owner fixtures. NeroCompanion has no member concept (one owner per companion).
 */
class CompanionMapLayerProviderTest {
    private static final Identifier OVERWORLD_ID = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER_ID = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, OVERWORLD_ID);
    private static final ResourceKey<Level> MOON =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("maptest", "moon"));

    private static final UUID ENTITLED = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID THIRD = UUID.randomUUID();

    private static HomePos at(Identifier dim, int x, int z) {
        return new HomePos(dim, new BlockPos(x, 64, z));
    }

    private static CompanionRecord record(UUID owner, String type, String name, Optional<HomePos> home) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", type), CompanionMode.FOLLOW, 5_000L, List.of(), 0,
                Optional.ofNullable(name), Optional.empty(), Optional.empty(), 7L, home, new CompoundTag());
    }

    /** A real ledger and a fake rest of the world, serving both the link view and the map source. */
    private static final class World implements CompanionMapSource, CompanionLinkView {
        final RecoveryBook book = new RecoveryBook(new AtomicLong(100L)::incrementAndGet);
        final Map<UUID, HomePos> loaded = new HashMap<>();
        final Map<HomePos, HomeKind> homes = new HashMap<>();

        CompanionRecord deploy(CompanionRecord record, HomePos where, boolean isLoaded) {
            book.live(record, UUID.randomUUID(), where, 10L, 1L);
            if (isLoaded) loaded.put(record.companionId(), where);
            return record;
        }

        CompanionRecord lose(CompanionRecord record, HomePos where) {
            UUID entity = UUID.randomUUID();
            book.live(record, entity, where, 10L, 1L);
            book.died(record, entity, where, 20L, 2L);
            return record;
        }

        // --- map source
        @Override
        public List<LedgerEntry> entriesOf(MapViewer viewer) {
            return book.entriesOf(viewer.id());
        }

        @Override
        public Optional<HomePos> liveAt(MapViewer viewer, LedgerEntry entry) {
            return entry.is(LedgerEntry.State.LIVE) ? Optional.ofNullable(loaded.get(entry.companionId()))
                    : Optional.empty();
        }

        @Override
        public boolean isBay(MapViewer viewer, HomePos home) {
            return homeKind(home) == HomeKind.BAY;
        }

        // --- link view
        @Override
        public long nowMillis() {
            return 1_789_000_000_000L;
        }

        @Override
        public List<LedgerEntry> entriesOf(UUID owner) {
            return book.entriesOf(owner);
        }

        @Override
        public Optional<Live> live(LedgerEntry entry) {
            HomePos where = loaded.get(entry.companionId());
            return where == null ? Optional.empty() : Optional.of(new Live(CompanionMode.FOLLOW, 50, where,
                    entry.snapshot().name(), 0, Optional.empty(), Optional.empty(), entry.snapshot().home(), List.of()));
        }

        @Override
        public Optional<HomePos> ownerAt(UUID owner) {
            return Optional.empty();
        }

        @Override
        public int chargePercent(CompanionRecord record) {
            return 50;
        }

        @Override
        public List<CompanionMode> supportedModes(Identifier type, CompanionMode current) {
            return List.of(current);
        }

        @Override
        public HomeKind homeKind(HomePos home) {
            return homes.getOrDefault(home, HomeKind.BAY);
        }

        @Override
        public Optional<SalvageView> salvage(UUID owner) {
            return Optional.empty();
        }
    }

    private static final HomePos BAY_A = at(OVERWORLD_ID, 40, -30);
    private static final HomePos BAY_B = at(OVERWORLD_ID, -60, 20);
    private static final HomePos PAD = at(OVERWORLD_ID, 70, 70);

    private static World seeded() {
        World w = new World();
        w.deploy(record(ENTITLED, "scout_drone", "Pip", Optional.of(BAY_B)), at(OVERWORLD_ID, 10, 5), true);
        w.deploy(record(ENTITLED, "carry_bot", null, Optional.empty()), at(OVERWORLD_ID, -20, 15), false);
        w.lose(record(ENTITLED, "mining_bot", "Digger", Optional.empty()), at(OVERWORLD_ID, 100, 100));
        w.deploy(record(ENTITLED, "repair_bot", null, Optional.empty()), at(NETHER_ID, 5, 5), true);
        w.book.parked(record(ENTITLED, "combat_bot", null, Optional.of(BAY_A)), BAY_A, 10L, 1L);
        w.book.toCapsule(record(ENTITLED, "personal_drone", null, Optional.empty()), 10L, 1L);
        CompanionRecord padHome = record(ENTITLED, "scrapling", null, Optional.of(PAD));
        w.homes.put(PAD, CompanionLinkView.HomeKind.PAD);
        w.deploy(padHome, at(NETHER_ID, 9, 9), false);
        w.deploy(record(THIRD, "scout_drone", "Trudy's drone", Optional.of(at(OVERWORLD_ID, 1, 1))),
                at(OVERWORLD_ID, 3, 3), true);
        w.book.parked(record(THIRD, "carry_bot", null, Optional.empty()), at(OVERWORLD_ID, 2, 2), 10L, 1L);
        return w;
    }

    private static MapViewer online(UUID id, int x, int z) {
        return MapViewer.forTest(id, null, OVERWORLD, new BlockPos(x, 64, z));
    }

    private static MapQueryResult query(World world, MapViewer viewer, ResourceKey<Level> dim, MapBounds bounds) {
        MapLayerRegistry.register(new CompanionMapLayerProvider(world));
        return MapLayerRegistry.query("nerocompanion", viewer, dim, bounds);
    }

    private static List<MapMarker> layer(MapQueryResult result, Identifier layer) {
        return result.markers().stream().filter(m -> m.layer().equals(layer)).toList();
    }

    @Test
    void passesCoreConformance() {
        MapLayerConformance.create(new CompanionMapLayerProvider(seeded()))
                .viewers(online(ENTITLED, 0, 0), online(STRANGER, 2, 0))
                .dimensions(OVERWORLD, MOON)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenIds(THIRD)
                .forbiddenText("Trudy")
                .expectEntitledSees(CompanionMapLayers.COMPANIONS, 3)
                .expectEntitledSees(CompanionMapLayers.HOME_BAYS, 2)
                .verify();
    }

    @Test
    void liveLostAndBaysFollowTheirStates() {
        MapQueryResult result = query(seeded(), online(ENTITLED, 0, 0), OVERWORLD, MapBounds.around(0, 0, 512));
        List<MapMarker> companions = layer(result, CompanionMapLayers.COMPANIONS);
        assertEquals(3, companions.size(), "loaded, unloaded-last-seen and lost; not parked, capsule or other world");
        MapMarker nearest = companions.get(0);
        assertEquals(10, nearest.x());
        assertEquals("Pip", nearest.label().text());
        assertTrue(nearest.details().isEmpty(), "a loaded companion is where it stands");
        assertEquals("map.nerocompanion.companion.last_seen", companions.get(1).details().get(0).text());
        MapMarker lost = companions.get(2);
        assertEquals(CompanionMapLayers.ICON_LOST, lost.icon());
        assertEquals(100, lost.x(), "a lost companion is at its last-known spot");

        List<MapMarker> bays = layer(result, CompanionMapLayers.HOME_BAYS);
        assertEquals(2, bays.size(), "the parked Bay and the home Bay; a Charging Pad home is not a Bay");
        Set<String> parked = bays.stream().map(m -> m.details().get(0).args().get(0)).collect(Collectors.toSet());
        assertEquals(Set.of("0", "1"), parked);
    }

    // --- Parity: map versus link --------------------------------------------------------------

    private static JsonObject link(World world, UUID player, String section) {
        return new CompanionLinkSnapshots(() -> world).snapshot(player, section, Map.of());
    }

    /** Roster ids the link places in the Overworld (not parked, not in a capsule). */
    private static Set<String> linkCompanions(World world, UUID player) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement e : link(world, player, CompanionLinkModule.SECTION_ROSTER).getAsJsonArray("companions")) {
            JsonObject row = e.getAsJsonObject();
            if (!row.get("parked").getAsBoolean() && "Overworld".equals(row.get("dimension").getAsString())) {
                out.add(row.get("id").getAsString());
            }
        }
        return out;
    }

    /** The link's Bays as "index:parked-count". All seeded Bays are in the Overworld. */
    private static Set<String> linkBays(World world, UUID player) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement e : link(world, player, CompanionLinkModule.SECTION_BAY).getAsJsonArray("bays")) {
            JsonObject bay = e.getAsJsonObject();
            out.add(bay.get("index").getAsInt() + ":" + bay.getAsJsonArray("slots").size());
        }
        return out;
    }

    private static MapQueryResult mapAll(World world, UUID player) {
        return query(world, MapViewer.forTest(player, null, OVERWORLD, new BlockPos(0, 64, 0)), OVERWORLD,
                MapBounds.around(0, 0, 4096));
    }

    private static Set<String> mapCompanions(World world, UUID player) {
        return layer(mapAll(world, player), CompanionMapLayers.COMPANIONS).stream()
                .map(m -> m.id().substring("companion/".length()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> mapBays(World world, UUID player) {
        Set<String> out = new LinkedHashSet<>();
        List<MapMarker> bays = new ArrayList<>(layer(mapAll(world, player), CompanionMapLayers.HOME_BAYS));
        for (MapMarker m : bays) {
            out.add(m.label().args().get(0) + ":" + m.details().get(0).args().get(0));
        }
        return out;
    }

    @Test
    void mapAndLinkAgreeForOwnerStrangerThirdAndErasedOwner() {
        World world = seeded();
        for (UUID player : List.of(ENTITLED, STRANGER, THIRD)) {
            assertEquals(linkCompanions(world, player), mapCompanions(world, player));
            assertEquals(linkBays(world, player), mapBays(world, player));
        }
        assertEquals(3, mapCompanions(world, ENTITLED).size());
        assertTrue(mapCompanions(world, STRANGER).isEmpty() && mapBays(world, STRANGER).isEmpty());
        world.book.purgeOwner(ENTITLED);
        assertEquals(linkCompanions(world, ENTITLED), mapCompanions(world, ENTITLED));
        assertEquals(linkBays(world, ENTITLED), mapBays(world, ENTITLED));
        assertTrue(mapCompanions(world, ENTITLED).isEmpty() && mapBays(world, ENTITLED).isEmpty(),
                "an erased owner sees nothing on either surface");
    }
}
