package za.co.neroland.nerocompanion.entity.type;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.registry.ModEntities;

/**
 * The server's companion type registry, read from {@code data/<namespace>/companion_types/*.json}.
 *
 * <p>Loaded the way the family's other data registries are (NeroQuests' {@code QuestDefinitions} /
 * {@code ContractDefinitions}): lazily, from the running server's {@link ResourceManager}, and cached against
 * that manager <em>instance</em>. {@code /reload} swaps the manager, so the next lookup re-reads the files -
 * a reload listener with no per-loader registration code. Callers that cache a {@link CompanionType} compare
 * {@link #generation()} to notice a reload.
 *
 * <p>Bad files never crash the server: every problem becomes a {@link ValidationIssue}, logged once at WARN
 * (resource ids and messages only) and listed by {@code /nscomp types}.
 */
public final class CompanionTypes {

    public static final String DIRECTORY = "companion_types";
    private static final String EXTENSION = ".json";

    private static MinecraftServer loadedFor;
    private static ResourceManager loadedFrom;
    private static Map<Identifier, CompanionType> types = Map.of();
    private static List<ValidationIssue> issues = List.of();
    private static int generation;

    private CompanionTypes() {
    }

    /** Every loaded type, by id (sorted). */
    public static synchronized Map<Identifier, CompanionType> forServer(MinecraftServer server) {
        if (server != loadedFor || server.getResourceManager() != loadedFrom) {
            loadedFor = server;
            loadedFrom = server.getResourceManager();
            List<ValidationIssue> collected = new ArrayList<>();
            types = load(loadedFrom, collected);
            issues = List.copyOf(collected);
            generation++;
            for (ValidationIssue issue : issues) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] companion type: {}", issue.describe());
            }
        }
        return types;
    }

    /** The type for an id, if loaded. */
    public static Optional<CompanionType> get(MinecraftServer server, Identifier id) {
        return Optional.ofNullable(forServer(server).get(id));
    }

    /** Problems found by the last load. */
    public static synchronized List<ValidationIssue> validationIssues() {
        return issues;
    }

    /** Bumped on every (re)load; entities compare it to refresh a cached type. */
    public static synchronized int generation() {
        return generation;
    }

    /** The server stopped: drop the references to it and what was read from it. */
    public static synchronized void serverStopped() {
        loadedFor = null;
        loadedFrom = null;
        types = Map.of();
        issues = List.of();
    }

    private static Map<Identifier, CompanionType> load(ResourceManager resources, List<ValidationIssue> collected) {
        Map<Identifier, CompanionType> loaded = new TreeMap<>();
        Map<Identifier, Resource> files =
                resources.listResources(DIRECTORY, file -> file.getPath().endsWith(EXTENSION));
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            String path = file.getKey().getPath();
            Identifier id = Identifier.fromNamespaceAndPath(file.getKey().getNamespace(),
                    path.substring(DIRECTORY.length() + 1, path.length() - EXTENSION.length()));
            try (BufferedReader reader = file.getValue().openAsReader()) {
                CompanionTypeParser.parse(id, JsonParser.parseReader(reader), ModEntities::locomotionOf,
                        WorkGoals.known(), collected).ifPresent(type -> loaded.put(id, type));
            } catch (Exception e) {
                collected.add(new ValidationIssue(ValidationIssue.Severity.DROPPED, id, "(file)",
                        "could not be read (" + e.getClass().getSimpleName() + ")"));
            }
        }
        return java.util.Collections.unmodifiableMap(loaded);
    }
}
