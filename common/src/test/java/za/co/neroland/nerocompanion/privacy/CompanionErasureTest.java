package za.co.neroland.nerocompanion.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerocompanion.compat.creatures.CreaturePets;
import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.incubation.IncubatorStore;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.salvage.DeathWindows;
import za.co.neroland.nerocompanion.salvage.HoldBook;

/**
 * Erasure clears every player-keyed store, checked with Core's {@link ErasureConformance} harness
 * on the plain JVM (server {@code null}): the real store objects (Recovery Ledger book, Salvage Hold book, Capsule
 * Vault, incubator store, NeroCreatures pets, death windows) are seeded for the subject and a bystander, erased through
 * {@link CompanionErasure#eraseStores} - the same fan-out the production eraser runs - and every refreshed backup is
 * captured to prove it no longer holds the subject. Live-companion unbinding needs a running world; its record
 * transform is tested in {@code ReclaimRecordsTest}.
 *
 * <p>Core's isolation pass deliberately logs one "Data eraser ... failed" warning per run; that is the assertion working.
 */
class CompanionErasureTest {

    private static final HomePos HOME = new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(5, 70, 5));

    private final UUID subject = UUID.randomUUID();
    private final UUID bystander = UUID.randomUUID();
    private final RecoveryBook ledger = new RecoveryBook();
    private final HoldBook<String> hold = new HoldBook<>();
    private final CapsuleVault vault = new CapsuleVault();
    private final IncubatorStore incubators = new IncubatorStore();
    private final DeathWindows windows = new DeathWindows();
    private final PrivacyPrefs prefs = new PrivacyPrefs();

    @TempDir
    Path exports;
    /** store name -> whether the subject was still in the store when its backup was refreshed. */
    private final Map<String, Boolean> backupHeldSubject = new LinkedHashMap<>();
    private final List<PlayerDataEraser> registered = new ArrayList<>();
    private final List<UUID> pets = new ArrayList<>();
    /** Distinct incubator positions per seeded player. */
    private int seeded;

    @AfterEach
    void cleanUp() {
        registered.forEach(PlayerDataErasure::unregister);
        pets.forEach(CreaturePets::released);
        CompanionPrivacy.COOLDOWN.clear();
        CompanionExport.overrideDirectory(null);
    }

    private static CompanionRecord record(UUID owner) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"), CompanionMode.FOLLOW, 100L, List.of(),
                300, Optional.of("Rusty"), Optional.empty(), Optional.empty(), 1L, Optional.of(HOME), new CompoundTag());
    }

    private void seed(UUID player) {
        ledger.live(record(player), UUID.randomUUID(), HOME, 0L, 0L);
        ledger.toCapsule(record(player), 0L, 0L);
        hold.add(player, UUID.randomUUID(), "pickaxe", 0L, 100L, 0L);
        vault.store(record(player));
        incubators.put(new HomePos(HOME.dimension(), new BlockPos(++seeded, 64, 0)), new Incubation(player, 100));
        UUID pet = UUID.randomUUID();
        pets.add(pet);
        CreaturePets.tamed(player, pet, "nerocreatures:glimmer");
        windows.open(player, "minecraft:overworld", 0, 64, 0, 0L, 1200L);
        prefs.set(player, PrivacyPrefs.Setting.NAMES, true, 1L);
        try {
            CompanionExport.write(exports, player, new JsonObject());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        CompanionPrivacy.COOLDOWN.tryAcquire(player, 0L, 60_000L);
    }

    private boolean exportHeld(UUID player) {
        return CompanionExport.exists(exports, player) || CompanionPrivacy.COOLDOWN.holds(player);
    }

    private CompanionErasure.Store store(String name, BiConsumer<MinecraftServer, UUID> erase, Predicate<UUID> holds) {
        return new CompanionErasure.Store() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                erase.accept(server, player);
            }

            @Override
            public void backup(MinecraftServer server) {
                backupHeldSubject.put(name, holds.test(subject));
            }
        };
    }

    private List<CompanionErasure.Store> stores() {
        return List.of(
                store("recovery_ledger", (s, p) -> ledger.forgetOwner(p), ledger::holdsFor),
                store("salvage_hold", (s, p) -> {
                    windows.forget(p);
                    hold.forgetOwner(p);
                }, p -> hold.holdsFor(p) || windows.holds(p)),
                store("capsule_vault", (s, p) -> vault.forgetOwner(p), vault::holdsFor),
                store("incubators", (s, p) -> incubators.forgetOwner(p), p -> !incubators.placedBy(p).isEmpty()),
                store("creature_pets", (s, p) -> CreaturePets.forgetOwner(p), p -> !CreaturePets.petsOf(p).isEmpty()),
                store("privacy", (s, p) -> prefs.forget(p), prefs::holdsFor),
                store("exports", (s, p) -> {
                    CompanionPrivacy.COOLDOWN.forget(p);
                    CompanionExport.delete(exports, p);
                }, this::exportHeld));
    }

    @Test
    void erasureClearsEveryStoreAndItsBackupAndKeepsTheBystander() {
        seed(subject);
        seed(bystander);
        List<CompanionErasure.Store> stores = stores();
        PlayerDataEraser eraser = (server, player) -> CompanionErasure.eraseStores(stores, server, player);
        PlayerDataErasure.register(eraser);
        registered.add(eraser);

        ErasureConformance.create()
                .probe("nerocompanion:recovery_ledger", ledger::holdsFor)
                .probe("nerocompanion:salvage_hold", hold::holdsFor)
                .probe("nerocompanion:death_windows", windows::holds)
                .probe("nerocompanion:capsule_vault", vault::holdsFor)
                .probe("nerocompanion:incubators", p -> !incubators.placedBy(p).isEmpty())
                .probe("nerocompanion:creature_pets", p -> !CreaturePets.petsOf(p).isEmpty())
                .probe("nerocompanion:privacy", prefs::holdsFor)
                .probe("nerocompanion:exports", this::exportHeld)
                .verify(null, subject);

        assertEquals(List.of("recovery_ledger", "salvage_hold", "capsule_vault", "incubators", "creature_pets",
                "privacy", "exports"),
                List.copyOf(backupHeldSubject.keySet()), "every store refreshed its backup after erasing");
        backupHeldSubject.forEach((name, held) -> assertFalse(held, name + " backup still held the subject"));

        assertTrue(ledger.holdsFor(bystander));
        assertTrue(hold.holdsFor(bystander));
        assertTrue(vault.holdsFor(bystander));
        assertFalse(incubators.placedBy(bystander).isEmpty());
        assertFalse(CreaturePets.petsOf(bystander).isEmpty());
        assertTrue(windows.holds(bystander));
        assertTrue(prefs.holdsFor(bystander));
        assertTrue(exportHeld(bystander));
    }

    @Test
    void theConformanceStoresMatchTheProductionErasureStores() {
        // init() is idempotent and needs no server; the production eraser it registers is harmless with server null.
        CompanionErasure.init();
        List<String> production = CompanionErasure.stores().stream().map(CompanionErasure.Store::name).toList();
        List<String> tested = new ArrayList<>(stores().stream().map(CompanionErasure.Store::name).toList());
        // session_memory (HUD board, loaded-entity index, mining focus and corners) needs a running server to seed.
        tested.add("session_memory");
        assertEquals(new java.util.TreeSet<>(tested), new java.util.TreeSet<>(production),
                "every production store is covered by the conformance run, and nothing else");
    }

    @Test
    void theProductionExportStoreDeletesTheFileAndTheCooldownOfTheSubjectOnly() throws IOException {
        CompanionExport.overrideDirectory(exports);
        CompanionExport.write(exports, subject, new JsonObject());
        CompanionExport.write(exports, bystander, new JsonObject());
        CompanionPrivacy.COOLDOWN.tryAcquire(subject, 0L, 60_000L);
        CompanionPrivacy.COOLDOWN.tryAcquire(bystander, 0L, 60_000L);
        List<CompanionErasure.Store> production = CompanionPrivacy.erasureStores();
        assertEquals(List.of("privacy", "exports"), production.stream().map(CompanionErasure.Store::name).toList());

        CompanionErasure.eraseStores(production.subList(1, 2), null, subject);

        assertFalse(CompanionExport.exists(exports, subject));
        assertFalse(CompanionPrivacy.COOLDOWN.holds(subject));
        assertTrue(CompanionExport.exists(exports, bystander));
        assertTrue(CompanionPrivacy.COOLDOWN.holds(bystander));
    }

    @Test
    void oneFailingStoreDoesNotStopTheOthersAndIsReported() {
        seed(subject);
        List<CompanionErasure.Store> stores = new ArrayList<>();
        stores.add(store("broken", (s, p) -> {
            throw new IllegalStateException("boom");
        }, p -> false));
        stores.addAll(stores());
        assertThrows(IllegalStateException.class, () -> CompanionErasure.eraseStores(stores, null, subject));
        assertFalse(ledger.holdsFor(subject), "stores after the broken one still ran");
        assertFalse(vault.holdsFor(subject));
    }
}
