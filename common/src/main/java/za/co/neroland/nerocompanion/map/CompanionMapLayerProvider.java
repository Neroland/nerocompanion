package za.co.neroland.nerocompanion.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * Two {@link MapScope#OWNER} layers over the viewer's own Recovery Ledger entries, re-checked against the stored
 * owner exactly as the link module does (the link's rule is package-private, so it is re-implemented here and a
 * parity test keeps the two in step):
 *
 * <ul>
 *   <li>{@code companions}: a LIVE companion at its loaded entity's position, or at its last-known spot while
 *       unloaded; a LOST companion at its last-known spot (the reason this layer exists). Parked companions are on
 *       the Bay layer; a companion in a Capsule has no position.</li>
 *   <li>{@code home_bays}: the same Bays the link's {@code bay} section lists (where the viewer's companions are
 *       parked, plus homes that are Bays), numbered the same way.</li>
 * </ul>
 *
 * <p>A companion's name is the owner's own text and appears only on the owner's own marker. Marker ids are the
 * companion id or the Bay's block position; neither names a player. Positions are read on each query, never kept.
 */
public final class CompanionMapLayerProvider implements MapLayerProvider {

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.of(CompanionMapLayers.COMPANIONS, "map.layer.nerocompanion.companions",
                    CompanionMapLayers.ICON_COMPANION, MapScope.OWNER),
            MapLayerInfo.of(CompanionMapLayers.HOME_BAYS, "map.layer.nerocompanion.home_bays",
                    CompanionMapLayers.ICON_BAY, MapScope.OWNER));

    private final CompanionMapSource source;

    public CompanionMapLayerProvider(CompanionMapSource source) {
        this.source = source;
    }

    @Override
    public String modId() {
        return NeroCompanionCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    private record Placed(MapMarker.Builder marker, BlockPos pos) { }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        if (viewer == null || dimension == null || bounds == null) return;
        List<LedgerEntry> entries = own(source.entriesOf(viewer), viewer.id());
        if (entries.isEmpty()) return;
        Identifier dim = dimension.identifier();
        int originX = viewer.position().map(BlockPos::getX).orElse(bounds.centreX());
        int originZ = viewer.position().map(BlockPos::getZ).orElse(bounds.centreZ());

        if (!sink.isFull(CompanionMapLayers.COMPANIONS)) {
            List<Placed> companions = new ArrayList<>();
            for (LedgerEntry entry : entries) {
                Placed placed = companion(viewer, entry, dimension, dim, bounds);
                if (placed != null) companions.add(placed);
            }
            emit(companions, CompanionMapLayers.COMPANIONS, originX, originZ, sink);
        }

        if (!sink.isFull(CompanionMapLayers.HOME_BAYS)) {
            List<HomePos> bays = bays(viewer, entries);
            List<Placed> placed = new ArrayList<>();
            for (int i = 0; i < bays.size(); i++) {
                HomePos bay = bays.get(i);
                if (!bay.dimension().equals(dim) || !bounds.contains(bay.pos().getX(), bay.pos().getZ())) continue;
                int parked = 0;
                for (LedgerEntry entry : entries) {
                    if (entry.is(State.PARKED) && entry.bay().filter(bay::equals).isPresent()) parked++;
                }
                placed.add(new Placed(MapMarker.builder(bayId(bay), CompanionMapLayers.HOME_BAYS, dimension, bay.pos())
                        .label(MapLabel.translatable("map.nerocompanion.home_bay", Integer.toString(i + 1)))
                        .detail(MapLabel.translatable("map.nerocompanion.home_bay.parked", Integer.toString(parked))),
                        bay.pos()));
            }
            emit(placed, CompanionMapLayers.HOME_BAYS, originX, originZ, sink);
        }
    }

    private Placed companion(MapViewer viewer, LedgerEntry entry, ResourceKey<Level> dimension, Identifier dim,
            MapBounds bounds) {
        boolean lost = entry.is(State.LOST);
        if (!lost && !entry.is(State.LIVE)) return null;
        Optional<HomePos> live = lost ? Optional.empty() : source.liveAt(viewer, entry);
        Optional<HomePos> at = live.isPresent() ? live : entry.lastKnown();
        if (at.isEmpty() || !at.get().dimension().equals(dim)) return null;
        BlockPos pos = at.get().pos();
        if (!bounds.contains(pos.getX(), pos.getZ())) return null;
        CompanionRecord record = entry.snapshot();
        MapMarker.Builder marker = MapMarker.builder(companionId(entry), CompanionMapLayers.COMPANIONS, dimension, pos)
                .label(name(record));
        if (lost) {
            marker.icon(CompanionMapLayers.ICON_LOST).detail(MapLabel.translatable("map.nerocompanion.companion.lost"));
        } else if (live.isEmpty()) {
            marker.detail(MapLabel.translatable("map.nerocompanion.companion.last_seen"));
        }
        return new Placed(marker, pos);
    }

    private static void emit(List<Placed> placed, Identifier layer, int x, int z, MapLayerSink sink) {
        placed.sort(Comparator.comparingLong(p -> distanceSq(p.pos(), x, z)));
        for (Placed p : placed) {
            if (sink.isFull(layer)) return;
            sink.marker(p.marker().build());
        }
    }

    /** The viewer's entries, re-checked against the stored owner (never trust a source to have scoped). */
    static List<LedgerEntry> own(List<LedgerEntry> entries, UUID viewer) {
        List<LedgerEntry> out = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            if (viewer.equals(entry.owner())) out.add(entry);
        }
        return out;
    }

    /**
     * The viewer's Bays in the link module's order: Bays they have companions parked in, then homes that are Bays,
     * sorted by dimension and position so the numbering matches the link's {@code bay} section.
     */
    List<HomePos> bays(MapViewer viewer, List<LedgerEntry> entries) {
        Set<HomePos> found = new LinkedHashSet<>();
        for (LedgerEntry entry : entries) {
            if (entry.is(State.PARKED) && entry.bay().isPresent()) found.add(entry.bay().get());
        }
        for (LedgerEntry entry : entries) {
            entry.snapshot().home().filter(h -> !found.contains(h) && source.isBay(viewer, h)).ifPresent(found::add);
        }
        List<HomePos> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparing((HomePos p) -> p.dimension().toString())
                .thenComparingInt(p -> p.pos().getX()).thenComparingInt(p -> p.pos().getY())
                .thenComparingInt(p -> p.pos().getZ()));
        return ordered;
    }

    /** The owner's own name for the companion, else its type's lang key. */
    static MapLabel name(CompanionRecord record) {
        Optional<String> name = record.name().filter(n -> !n.isBlank());
        if (name.isPresent()) return MapLabel.literal(name.get());
        Identifier type = record.type();
        return MapLabel.translatable("entity." + type.getNamespace() + "." + type.getPath().replace('/', '.'));
    }

    static String companionId(LedgerEntry entry) {
        return "companion/" + entry.companionId();
    }

    static String bayId(HomePos bay) {
        return "bay/" + bay.pos().getX() + "_" + bay.pos().getY() + "_" + bay.pos().getZ();
    }

    private static long distanceSq(BlockPos pos, int x, int z) {
        long dx = (long) pos.getX() - x;
        long dz = (long) pos.getZ() - z;
        return dx * dx + dz * dz;
    }
}
