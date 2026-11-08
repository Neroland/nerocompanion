package za.co.neroland.nerocompanion.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.compat.creatures.CreaturePets;
import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.incubation.IncubatorStore;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.salvage.HoldBook;

/**
 * The data-subject access export (GDPR Art. 15/20, POPIA s23) behind {@code /nscomp export}: everything NeroCompanion
 * holds about <b>one player</b>, as one JSON file at {@code <world>/nerocompanion/exports/<uuid>.json}.
 *
 * <p>Never another player's data: only rows keyed by the subject's own UUID are read into the document (their Recovery
 * Ledger entries with the positions they hold, their Capsule Vault entries, their Salvage Hold, the incubators holding
 * their eggs, their NeroCreatures pets, their privacy settings and activity stamp). The path is built from the
 * subject's own UUID and checked to stay inside the export folder; on a POSIX file system the folder and file are
 * restricted to the server's own user. An export is deleted by an erasure request, by the inactive-owner retention
 * sweep and after {@link #RETENTION_DAYS} days. Failures are logged by exception class only (the message holds the
 * path, and the file name is the UUID).
 */
public final class CompanionExport {

    public static final int FORMAT_VERSION = 1;
    /** Export files are deleted this many days after they were written. */
    public static final int RETENTION_DAYS = 7;
    /** Relative to the world folder; shown to the player. */
    public static final String RELATIVE_DIR = "nerocompanion/exports";

    private static final long DAY_MS = 86_400_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    /** The privacy settings in force, for the document's {@code privacy} block. */
    public record Settings(boolean serverAllowsNames, int retentionDays, int lostAfterOfflineDays) {
    }

    /** The stores the document reads; any may be null (an absent store holds nothing). */
    public record Sources(@Nullable RecoveryBook ledger, @Nullable CapsuleVault vault, @Nullable HoldBook<?> hold,
            @Nullable IncubatorStore incubators, List<CreaturePets.Pet> pets, @Nullable PrivacyPrefs prefs) {
    }

    private CompanionExport() {
    }

    // --- the document (pure) ---------------------------------------------------------------------------

    /**
     * The export document for {@code subject} (plain-JVM testable). {@code describeItem} turns one held item into
     * JSON (item id and count on a server). Other players' rows are never read into it.
     */
    public static JsonObject build(UUID subject, Sources sources, Settings settings,
            Function<Object, JsonElement> describeItem, long nowMillis) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", NeroCompanionCommon.MOD_ID);
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported_at", Instant.ofEpochMilli(nowMillis).toString());
        root.addProperty("subject", subject.toString());

        JsonObject privacy = new JsonObject();
        PrivacyPrefs.Row row = sources.prefs() == null ? PrivacyPrefs.Row.DEFAULT : sources.prefs().row(subject);
        privacy.addProperty("names_hidden_from_others", row.namesOff());
        privacy.addProperty("chatter_off", row.chatterOff());
        privacy.addProperty("push_off", row.pushOff());
        privacy.add("last_seen", row.lastSeen() > 0L ? new com.google.gson.JsonPrimitive(
                Instant.ofEpochMilli(row.lastSeen()).toString()) : JsonNull.INSTANCE);
        privacy.addProperty("server_allows_names", settings.serverAllowsNames());
        privacy.addProperty("retention_days", settings.retentionDays());
        privacy.addProperty("lost_after_offline_days", settings.lostAfterOfflineDays());
        root.add("privacy", privacy);

        JsonArray companions = new JsonArray();
        if (sources.ledger() != null) {
            for (LedgerEntry entry : sources.ledger().entriesOf(subject)) {
                JsonObject out = new JsonObject();
                out.addProperty("companion", entry.companionId().toString());
                out.addProperty("state", entry.state().id());
                out.add("last_known_position", pos(entry.lastKnown()));
                out.add("bay", pos(entry.bay()));
                out.addProperty("last_used", Instant.ofEpochMilli(Math.max(0L, entry.touchedAt())).toString());
                out.add("record", record(entry.snapshot()));
                companions.add(out);
            }
        }
        root.add("companions", companions);

        JsonArray vault = new JsonArray();
        if (sources.vault() != null) {
            for (Map.Entry<UUID, CapsuleVault.Entry> e : sources.vault().entriesOf(subject).entrySet()) {
                JsonObject out = new JsonObject();
                out.addProperty("companion", e.getKey().toString());
                out.add("home", pos(e.getValue().home()));
                out.addProperty("job", e.getValue().job().toString());
                out.addProperty("stored_at", Instant.ofEpochMilli(Math.max(0L, e.getValue().storedAt())).toString());
                vault.add(out);
            }
        }
        root.add("capsule_vault", vault);

        JsonElement hold = JsonNull.INSTANCE;
        if (sources.hold() != null) {
            Optional<? extends HoldBook.Hold<?>> held = sources.hold().get(subject);
            if (held.isPresent()) {
                JsonObject out = new JsonObject();
                JsonArray items = new JsonArray();
                for (Object item : held.get().items()) {
                    items.add(describeItem.apply(item));
                }
                out.add("items", items);
                out.addProperty("held_by_companions", held.get().holders().size());
                out.addProperty("last_used", Instant.ofEpochMilli(Math.max(0L, held.get().touchedAt())).toString());
                hold = out;
            }
        }
        root.add("salvage_hold", hold);

        JsonArray eggs = new JsonArray();
        if (sources.incubators() != null) {
            for (HomePos at : sources.incubators().placedBy(subject)) {
                JsonObject out = new JsonObject();
                out.add("incubator", pos(Optional.of(at)));
                out.addProperty("progress_percent", Math.round(sources.incubators().at(at)
                        .map(Incubation::progress).orElse(0.0F) * 100.0F));
                eggs.add(out);
            }
        }
        root.add("incubating_eggs", eggs);

        JsonArray pets = new JsonArray();
        for (CreaturePets.Pet pet : sources.pets()) {
            JsonObject out = new JsonObject();
            out.addProperty("pet", pet.petId().toString());
            out.addProperty("type", pet.type());
            out.addProperty("command", pet.command());
            out.addProperty("bond_points", pet.bondPoints());
            pets.add(out);
        }
        root.add("creature_pets", pets);

        JsonObject notes = new JsonObject();
        notes.addProperty("others", "No other player appears in this file.");
        notes.addProperty("world", "Your deployed companions and the Companion Bays they park in keep your player id, "
                + "their name and their home in the world save, like a tamed animal. They are listed above from the "
                + "recovery ledger.");
        notes.addProperty("items", "Capsules are items. A Capsule carries the companion's type, name, chips and looks "
                + "but never your player id or a position, wherever it is. The server cannot list items, so they are "
                + "not in this file.");
        notes.addProperty("milestones", "Your companion milestones and skin unlocks are flags in Neroland Core's "
                + "progression store; Core exports and erases them.");
        notes.addProperty("link", "The NeroLink module stores nothing of its own. It reads the data above for your "
                + "own app only. While it runs it remembers, for at most a minute and in memory only, which alert it "
                + "last sent you about each companion (so it does not repeat itself); that is forgotten on erasure. "
                + "The alerts themselves are kept by Neroland Core and NeroLink, which export and erase them.");
        notes.addProperty("erasure", "'/neroland data eraseme' removes everything above, turns your companions into "
                + "ownerless Capsules and deletes this file.");
        notes.addProperty("retention", "This file is deleted after " + RETENTION_DAYS + " days.");
        root.add("notes", notes);
        return root;
    }

    private static JsonObject record(CompanionRecord record) {
        JsonObject out = new JsonObject();
        out.addProperty("type", record.type().toString());
        out.add("name", record.name().<JsonElement>map(com.google.gson.JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        out.addProperty("mode", record.mode().name().toLowerCase(java.util.Locale.ROOT));
        out.addProperty("energy_fe", record.energyFe());
        out.addProperty("bond_points", record.bondPoints());
        JsonArray chips = new JsonArray();
        for (ChipStack chip : record.chips()) {
            chips.add(chip.chip() + " tier " + chip.tier());
        }
        out.add("chips", chips);
        out.add("skin", record.skin().<JsonElement>map(id -> new com.google.gson.JsonPrimitive(id.toString()))
                .orElse(JsonNull.INSTANCE));
        out.add("hat", record.hat().<JsonElement>map(id -> new com.google.gson.JsonPrimitive(id.toString()))
                .orElse(JsonNull.INSTANCE));
        out.add("home", pos(record.home()));
        out.addProperty("job", record.jobData().toString());
        return out;
    }

    private static JsonElement pos(Optional<HomePos> at) {
        if (at.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        JsonObject out = new JsonObject();
        out.addProperty("dimension", at.get().dimension().toString());
        out.addProperty("x", at.get().pos().getX());
        out.addProperty("y", at.get().pos().getY());
        out.addProperty("z", at.get().pos().getZ());
        return out;
    }

    public static String toJson(JsonObject document) {
        return GSON.toJson(document);
    }

    // --- files -------------------------------------------------------------------------------------------

    private static volatile @Nullable Path directoryOverride;

    /** Test seam: the export folder the erasers and the sweep use without a server ({@code null} restores). */
    public static void overrideDirectory(@Nullable Path directory) {
        directoryOverride = directory;
    }

    /** The export folder of {@code server} (or the test override); null without either. */
    public static @Nullable Path directory(@Nullable MinecraftServer server) {
        Path forced = directoryOverride;
        if (forced != null) {
            return forced;
        }
        return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("nerocompanion")
                .resolve("exports").normalize();
    }

    /** One subject's export path, refusing anything that escapes the export folder. */
    public static Path file(Path directory, UUID subject) {
        return file(directory, String.valueOf(subject));
    }

    static Path file(Path directory, String name) {
        Path root = directory.normalize();
        Path output = root.resolve(name + ".json").normalize();
        if (!output.startsWith(root) || output.getParent() == null || !output.getParent().equals(root)) {
            throw new IllegalStateException("Unsafe export path");
        }
        return output;
    }

    /** The path the player is told, relative to the world folder. */
    public static String relativePath(UUID subject) {
        return RELATIVE_DIR + "/" + subject + ".json";
    }

    /** Writes the document, replacing an earlier export. Server thread. */
    public static String write(Path directory, UUID subject, JsonObject document) throws IOException {
        Path output = file(directory, subject);
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, OWNER_DIRECTORY);
        } catch (UnsupportedOperationException | IOException bestEffort) {
            // Not POSIX (Windows), or not ours to re-mode: the platform defaults apply.
        }
        try {
            if (Files.exists(output)) {
                Files.setPosixFilePermissions(output, OWNER_FILE);
            } else {
                Files.createFile(output, PosixFilePermissions.asFileAttribute(OWNER_FILE));
            }
        } catch (UnsupportedOperationException notPosix) {
            // No POSIX permission view on this file system.
        }
        Files.writeString(output, toJson(document), StandardCharsets.UTF_8);
        return relativePath(subject);
    }

    /** Erasure and retention: an export must not outlive the data it was taken from. Never throws. */
    public static boolean delete(@Nullable Path directory, UUID subject) {
        if (directory == null || subject == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(directory, subject));
        } catch (IOException | RuntimeException failure) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] an export file could not be deleted ({}).",
                    failure.getClass().getName());
            return false;
        }
    }

    /** Conformance probe: does an export file exist for {@code subject}? */
    public static boolean exists(@Nullable Path directory, UUID subject) {
        try {
            return directory != null && subject != null && Files.exists(file(directory, subject));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Retention: deletes export files older than {@link #RETENTION_DAYS}. Returns how many. Never throws. */
    public static int purgeExpired(@Nullable Path directory, long nowMillis) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        long cutoff = nowMillis - RETENTION_DAYS * DAY_MS;
        int removed = 0;
        try (Stream<Path> listing = Files.list(directory)) {
            for (Path file : listing.toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")
                        && Files.getLastModifiedTime(file).toMillis() <= cutoff && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        } catch (IOException | RuntimeException failure) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] export retention sweep failed ({}).",
                    failure.getClass().getName());
        }
        return removed;
    }
}
