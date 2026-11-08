package za.co.neroland.nerocompanion.chip;

import java.io.BufferedReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * Which chips cannot be fitted together, read from {@code data/<namespace>/chip_rules/exclusive.json} in every
 * namespace ({@code {"pairs": [["a", "b"], ...]}}). Exclusivity is by kind (any tier). Read lazily and cached
 * against the server's {@link ResourceManager} instance like the type registry, so {@code /reload} picks up a
 * changed file. Unknown ids are ignored (one WARN, ids only).
 */
public final class ChipRules {

    public static final String DIRECTORY = "chip_rules";
    public static final String FILE = "exclusive.json";

    /** The pairs shipped in NeroCompanion's own file; also the fallback when no file can be read. */
    public static final Exclusions DEFAULT = new Exclusions(Set.of(
            Exclusions.key(ChipKinds.LAMP.id(), ChipKinds.SILENT_RUNNING.id()),
            Exclusions.key(ChipKinds.CHATTERBOX.id(), ChipKinds.HUSH.id()),
            Exclusions.key(ChipKinds.SPEED.id(), ChipKinds.ARMOUR.id())));

    private static ResourceManager loadedFrom;
    private static Exclusions current = DEFAULT;

    private ChipRules() {
    }

    /** An immutable set of unordered chip-kind pairs. */
    public record Exclusions(Set<String> pairs) {
        public Exclusions {
            pairs = Set.copyOf(pairs);
        }

        static String key(Identifier a, Identifier b) {
            String x = a.toString();
            String y = b.toString();
            return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
        }

        public boolean excludes(Identifier a, Identifier b) {
            return !a.equals(b) && this.pairs.contains(key(a, b));
        }

        /** The first installed kind {@code candidate} cannot share a companion with. */
        public Optional<Identifier> clashWith(Identifier candidate, List<Identifier> installed) {
            for (Identifier other : installed) {
                if (this.excludes(candidate, other)) {
                    return Optional.of(other);
                }
            }
            return Optional.empty();
        }
    }

    /** Parses one file's pairs into {@code into}. Malformed entries and unknown chip ids are reported, not fatal. */
    public static void parse(JsonElement json, Set<String> into, Consumer<String> warn) {
        if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("pairs")
                || !json.getAsJsonObject().get("pairs").isJsonArray()) {
            warn.accept("expected {\"pairs\": [[a, b], ...]}");
            return;
        }
        for (JsonElement entry : json.getAsJsonObject().getAsJsonArray("pairs")) {
            if (!entry.isJsonArray() || entry.getAsJsonArray().size() != 2) {
                warn.accept("a pair must be a two-element array");
                continue;
            }
            Identifier a = Identifier.tryParse(entry.getAsJsonArray().get(0).getAsString());
            Identifier b = Identifier.tryParse(entry.getAsJsonArray().get(1).getAsString());
            if (a == null || b == null || ChipKinds.byId(a).isEmpty() || ChipKinds.byId(b).isEmpty()) {
                warn.accept("unknown chip in pair " + entry);
                continue;
            }
            if (!a.equals(b)) {
                into.add(Exclusions.key(a, b));
            }
        }
    }

    /** The exclusions for the running server. */
    public static synchronized Exclusions forServer(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        if (resources != loadedFrom) {
            loadedFrom = resources;
            current = load(resources);
        }
        return current;
    }

    public static synchronized void serverStopped() {
        loadedFrom = null;
        current = DEFAULT;
    }

    private static Exclusions load(ResourceManager resources) {
        Set<String> pairs = new HashSet<>();
        boolean any = false;
        Map<Identifier, Resource> files = resources.listResources(DIRECTORY, file -> file.getPath().endsWith(FILE));
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            try (BufferedReader reader = file.getValue().openAsReader()) {
                parse(JsonParser.parseReader(reader), pairs, problem -> NeroCompanionCommon.LOGGER.warn(
                        "[NeroCompanion] chip rules {}: {}", file.getKey(), problem));
                any = true;
            } catch (Exception e) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] chip rules {} could not be read ({})", file.getKey(),
                        e.getClass().getSimpleName());
            }
        }
        return any ? new Exclusions(pairs) : DEFAULT;
    }
}
