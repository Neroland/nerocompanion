package za.co.neroland.nerocompanion.look;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.type.ValidationIssue;
import za.co.neroland.nerocompanion.registry.ModEntities;

/**
 * The server's skin and hat catalogue, read from {@code data/<ns>/skins/**} and {@code data/<ns>/hats/*.json} the
 * way {@code CompanionTypes} reads types: lazily from the running server's resource manager, reloaded with it
 * (datapacks may add or replace looks). Ids: {@code skins/personal_drone/graphite.json} is
 * {@code nerocompanion:personal_drone/graphite}, {@code skins/aurora.json} is the family skin
 * {@code nerocompanion:aurora}, {@code hats/hard_hat.json} is {@code nerocompanion:hard_hat}.
 */
public final class Looks {

    private static final String EXTENSION = ".json";

    /** The model variants a skin may name in {@code model_override} (the client registers one model per id). */
    public static final Set<Identifier> MODEL_OVERRIDES = Set.of(
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "scrapling/mossback"));

    private static MinecraftServer loadedFor;
    private static ResourceManager loadedFrom;
    private static Map<Identifier, Look> skins = Map.of();
    private static Map<Identifier, Look> hats = Map.of();
    private static List<ValidationIssue> issues = List.of();
    private static int generation;

    private Looks() {
    }

    private static synchronized void ensure(MinecraftServer server) {
        if (server == loadedFor && server.getResourceManager() == loadedFrom) {
            return;
        }
        loadedFor = server;
        loadedFrom = server.getResourceManager();
        List<ValidationIssue> collected = new ArrayList<>();
        Set<Identifier> types = new HashSet<>(ModEntities.COMPANION_TYPES);
        skins = load(loadedFrom, LookKind.SKIN, types, collected);
        hats = load(loadedFrom, LookKind.HAT, types, collected);
        issues = List.copyOf(collected);
        generation++;
        for (ValidationIssue issue : issues) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] look: {}", issue.describe());
        }
    }

    public static synchronized Map<Identifier, Look> skins(MinecraftServer server) {
        ensure(server);
        return skins;
    }

    public static synchronized Map<Identifier, Look> hats(MinecraftServer server) {
        ensure(server);
        return hats;
    }

    /** Skins then hats, each sorted by id (the catalogue payload's order). */
    public static synchronized List<Look> all(MinecraftServer server) {
        ensure(server);
        List<Look> out = new ArrayList<>(skins.values());
        out.addAll(hats.values());
        return out;
    }

    public static Optional<Look> get(MinecraftServer server, LookKind kind, Identifier id) {
        return Optional.ofNullable((kind == LookKind.HAT ? hats(server) : skins(server)).get(id));
    }

    /** Bumped on every (re)load. */
    public static synchronized int generation(MinecraftServer server) {
        ensure(server);
        return generation;
    }

    public static synchronized List<ValidationIssue> validationIssues() {
        return issues;
    }

    /** The standard skin id of a type: {@code <ns>:<type>/default}. */
    public static Identifier defaultSkin(Identifier type) {
        return Identifier.fromNamespaceAndPath(type.getNamespace(), type.getPath() + "/default");
    }

    public static synchronized void serverStopped() {
        loadedFor = null;
        loadedFrom = null;
        skins = Map.of();
        hats = Map.of();
        issues = List.of();
    }

    /** Look id from a file path relative to the kind's directory ({@code personal_drone/graphite.json}). */
    static Identifier idOf(String namespace, String relative) {
        String path = relative.endsWith(EXTENSION) ? relative.substring(0, relative.length() - EXTENSION.length())
                : relative;
        return Identifier.tryBuild(namespace, path);
    }

    private static Map<Identifier, Look> load(ResourceManager resources, LookKind kind, Set<Identifier> types,
            List<ValidationIssue> collected) {
        Map<Identifier, Look> loaded = new TreeMap<>();
        Map<Identifier, Resource> files =
                resources.listResources(kind.directory(), file -> file.getPath().endsWith(EXTENSION));
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            Identifier id = idOf(file.getKey().getNamespace(),
                    file.getKey().getPath().substring(kind.directory().length() + 1));
            if (id == null) {
                continue;
            }
            if (kind == LookKind.HAT && id.getPath().contains("/")) {
                collected.add(new ValidationIssue(ValidationIssue.Severity.DROPPED, id, "(file)",
                        "hats live directly in hats/, not in a sub-folder"));
                continue;
            }
            try (BufferedReader reader = file.getValue().openAsReader()) {
                LookParser.parse(id, kind, JsonParser.parseReader(reader), types, MODEL_OVERRIDES, collected)
                        .ifPresent(look -> loaded.put(id, look));
            } catch (Exception e) {
                collected.add(new ValidationIssue(ValidationIssue.Severity.DROPPED, id, "(file)",
                        "could not be read (" + e.getClass().getSimpleName() + ")"));
            }
        }
        return java.util.Collections.unmodifiableMap(loaded);
    }
}
