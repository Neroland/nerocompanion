package za.co.neroland.nerocompanion.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.nerocompanion.alert.AlertText;
import za.co.neroland.nerocompanion.bond.BondLevels;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.LedgerEntry.State;

/**
 * The read half of the link module (decision 013): sections {@code roster}, {@code salvage} and {@code bay}, every
 * one built from the requester's own Recovery Ledger entries (owner UUID) and nothing else. No coordinates, no
 * biome or structure, no owner or entity UUID and nothing about any other player ever leaves here: a position is
 * only an input to the coarse {@code areaHint} ({@code near_you}, {@code at_home}, {@code away}, {@code unknown}),
 * and a dimension only as its display name. Server thread (the bridge marshals calls onto it).
 */
public final class CompanionLinkSnapshots implements LinkSnapshotProvider {

    /** Within this many blocks of the online owner (same dimension): {@code near_you}. */
    static final double NEAR_YOU = 64.0D;
    /** Within this many blocks of home (same dimension): {@code at_home}. */
    static final double AT_HOME = 16.0D;

    private final Supplier<CompanionLinkView> view;

    public CompanionLinkSnapshots(Supplier<CompanionLinkView> view) {
        this.view = view;
    }

    @Override
    public String moduleId() {
        return CompanionLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return CompanionLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return CompanionLinkModule.SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        CompanionLinkView world = this.view.get();
        if (playerId == null || section == null) {
            return new JsonObject();
        }
        return switch (section) {
            case CompanionLinkModule.SECTION_ROSTER -> roster(world, playerId);
            case CompanionLinkModule.SECTION_SALVAGE -> salvage(world, playerId);
            case CompanionLinkModule.SECTION_BAY -> bay(world, playerId);
            default -> new JsonObject();
        };
    }

    // =================================================================================================
    // roster
    // =================================================================================================

    static JsonObject roster(CompanionLinkView world, UUID owner) {
        JsonObject out = new JsonObject();
        JsonArray rows = new JsonArray();
        out.addProperty("asOf", world == null ? System.currentTimeMillis() : world.nowMillis());
        out.add("companions", rows);
        if (world == null) {
            return out;
        }
        Optional<HomePos> ownerAt = world.ownerAt(owner);
        for (LedgerEntry entry : own(world, owner)) {
            rows.add(row(world, entry, ownerAt));
        }
        return out;
    }

    private static JsonObject row(CompanionLinkView world, LedgerEntry entry, Optional<HomePos> ownerAt) {
        CompanionRecord record = entry.snapshot();
        Optional<CompanionLinkView.Live> live = entry.is(State.LIVE) ? world.live(entry) : Optional.empty();
        CompanionMode mode = live.map(CompanionLinkView.Live::mode).orElse(record.mode());
        Optional<String> name = live.isPresent() ? live.get().name() : record.name();
        Optional<HomePos> home = live.isPresent() ? live.get().home() : record.home();
        Optional<HomePos> at = where(entry, live);

        JsonObject row = new JsonObject();
        row.addProperty("id", entry.companionId().toString());
        row.addProperty("type", coarseType(record.type()));
        row.addProperty("chassis", record.type().toString());
        row.addProperty("name", displayName(record.type(), name));
        addId(row, "skin", live.isPresent() ? live.get().skin() : record.skin());
        addId(row, "hat", live.isPresent() ? live.get().hat() : record.hat());
        row.addProperty("mode", mode.id());
        JsonArray modes = new JsonArray();
        List<CompanionMode> supportedModes = live.map(CompanionLinkView.Live::supportedModes)
                .filter(list -> !list.isEmpty()).orElseGet(() -> world.supportedModes(record.type(), mode));
        for (CompanionMode supported : supportedModes) {
            modes.add(supported.id());
        }
        row.add("supportedModes", modes);
        row.addProperty("charge", clampPercent(live.map(CompanionLinkView.Live::charge)
                .orElseGet(() -> world.chargePercent(record))));
        row.addProperty("chargeMax", 100);
        row.addProperty("dimension", entry.is(State.CAPSULE) ? "Unknown"
                : at.map(p -> dimensionName(p.dimension().toString())).orElse("Unknown"));
        row.addProperty("areaHint", areaHint(entry.state(), at, home, ownerAt));
        row.addProperty("lost", entry.is(State.LOST));
        row.addProperty("bond", BondLevels.level(live.map(CompanionLinkView.Live::bondPoints)
                .orElse(record.bondPoints())));
        row.addProperty("home", home.map(h -> world.homeKind(h).id()).orElse(CompanionLinkView.HomeKind.NONE.id()));
        row.addProperty("parked", entry.is(State.PARKED));
        return row;
    }

    /** Where the companion is, for the hint and the dimension name only: the entity, the Bay, or last known. */
    private static Optional<HomePos> where(LedgerEntry entry, Optional<CompanionLinkView.Live> live) {
        if (live.isPresent()) {
            return Optional.of(live.get().at());
        }
        if (entry.is(State.PARKED) && entry.bay().isPresent()) {
            return entry.bay();
        }
        if (entry.is(State.CAPSULE)) {
            return Optional.empty();
        }
        return entry.lastKnown();
    }

    /** The coarse hint (decision 013): never a coordinate, a biome or a structure name. */
    static String areaHint(State state, Optional<HomePos> at, Optional<HomePos> home, Optional<HomePos> ownerAt) {
        if (state == State.PARKED) {
            return "at_home";
        }
        if (state != State.LIVE || at == null || at.isEmpty()) {
            return "unknown";
        }
        HomePos here = at.get();
        if (ownerAt != null && ownerAt.isPresent() && within(here, ownerAt.get(), NEAR_YOU)) {
            return "near_you";
        }
        if (home != null && home.isPresent() && within(here, home.get(), AT_HOME)) {
            return "at_home";
        }
        return "away";
    }

    private static boolean within(HomePos a, HomePos b, double range) {
        return a.dimension().equals(b.dimension()) && a.pos().distSqr(b.pos()) <= range * range;
    }

    // =================================================================================================
    // salvage
    // =================================================================================================

    static JsonObject salvage(CompanionLinkView world, UUID owner) {
        JsonObject out = new JsonObject();
        out.addProperty("asOf", world == null ? System.currentTimeMillis() : world.nowMillis());
        Optional<CompanionLinkView.SalvageView> hold = world == null ? Optional.empty() : world.salvage(owner);
        boolean held = hold.isPresent() && hold.get().count() > 0;
        out.addProperty("held", held);
        out.addProperty("count", held ? hold.get().count() : 0);
        if (held) {
            out.addProperty("expiresAt", hold.get().expiresAtMillis());
        } else {
            out.add("expiresAt", JsonNull.INSTANCE);
        }
        return out;
    }

    // =================================================================================================
    // bay
    // =================================================================================================

    /** The Bays the requester's companions are parked in or call home, numbered 1..n; no positions. */
    static JsonObject bay(CompanionLinkView world, UUID owner) {
        JsonObject out = new JsonObject();
        JsonArray bays = new JsonArray();
        out.addProperty("asOf", world == null ? System.currentTimeMillis() : world.nowMillis());
        out.add("bays", bays);
        if (world == null) {
            return out;
        }
        List<LedgerEntry> entries = own(world, owner);
        Set<HomePos> found = new LinkedHashSet<>();
        for (LedgerEntry entry : entries) {
            if (entry.is(State.PARKED) && entry.bay().isPresent()) {
                found.add(entry.bay().get());
            }
        }
        for (LedgerEntry entry : entries) {
            entry.snapshot().home().filter(h -> !found.contains(h)
                    && world.homeKind(h) == CompanionLinkView.HomeKind.BAY).ifPresent(found::add);
        }
        List<HomePos> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparing((HomePos p) -> p.dimension().toString())
                .thenComparingInt(p -> p.pos().getX()).thenComparingInt(p -> p.pos().getY())
                .thenComparingInt(p -> p.pos().getZ()));
        int index = 1;
        for (HomePos at : ordered) {
            JsonObject bay = new JsonObject();
            bay.addProperty("index", index++);
            JsonArray slots = new JsonArray();
            for (LedgerEntry entry : entries) {
                if (entry.is(State.PARKED) && entry.bay().filter(at::equals).isPresent()) {
                    CompanionRecord record = entry.snapshot();
                    JsonObject slot = new JsonObject();
                    slot.addProperty("companionId", entry.companionId().toString());
                    slot.addProperty("type", coarseType(record.type()));
                    slot.addProperty("chassis", record.type().toString());
                    slot.addProperty("name", displayName(record.type(), record.name()));
                    slot.addProperty("charge", clampPercent(world.chargePercent(record)));
                    slots.add(slot);
                }
            }
            bay.add("slots", slots);
            bays.add(bay);
        }
        return out;
    }

    // =================================================================================================
    // shared
    // =================================================================================================

    /** The requester's entries, re-checked against the owner (never trust a view to have scoped). */
    private static List<LedgerEntry> own(CompanionLinkView world, UUID owner) {
        List<LedgerEntry> out = new ArrayList<>();
        for (LedgerEntry entry : world.entriesOf(owner)) {
            if (owner.equals(entry.owner())) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The app's icon class: {@code drone}, {@code bot} or {@code pet}. */
    static String coarseType(Identifier type) {
        String path = type.getPath();
        if (!"nerocompanion".equals(type.getNamespace()) || path.contains("scrapling") || path.endsWith("_pet")) {
            return "pet";
        }
        return path.endsWith("_drone") ? "drone" : "bot";
    }

    /** The stored (already filtered) name, else the type's English name. */
    static String displayName(Identifier type, Optional<String> name) {
        return name.filter(n -> !n.isBlank()).map(CompanionRecord::capName).filter(n -> !n.isEmpty())
                .orElseGet(() -> AlertText.typeName(type.getPath()));
    }

    private static void addId(JsonObject row, String key, Optional<Identifier> id) {
        if (id.isPresent()) {
            row.addProperty(key, id.get().toString());
        } else {
            row.add(key, JsonNull.INSTANCE);
        }
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** {@code minecraft:overworld} → "Overworld"; a modded {@code ns:deep_space} → "Deep Space". */
    static String dimensionName(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "Unknown";
        }
        switch (dimensionId) {
            case "minecraft:overworld" -> {
                return "Overworld";
            }
            case "minecraft:the_nether" -> {
                return "The Nether";
            }
            case "minecraft:the_end" -> {
                return "The End";
            }
            default -> {
            }
        }
        int colon = dimensionId.indexOf(':');
        String path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
        StringBuilder out = new StringBuilder();
        for (String word : path.toLowerCase(Locale.ROOT).split("[_/.\\-]+")) {
            if (word.isEmpty() || !word.codePoints().allMatch(Character::isLetterOrDigit)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        String name = out.toString();
        if (name.isEmpty()) {
            return "Unknown";
        }
        return name.length() > 48 ? name.substring(0, 48).trim() : name;
    }
}
