package za.co.neroland.nerocompanion.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.nerocompanion.compat.creatures.CreaturePets;
import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.incubation.IncubatorStore;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.salvage.HoldBook;

/**
 * {@code /nscomp export}: two players with the same kinds of data; the subject's file holds everything of theirs (with
 * the positions that are theirs) and nothing of the other player's - no UUID, no companion id, no name, no position.
 */
class CompanionExportTest {

    private static final long NOW = 1_900_000_000_000L;
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @TempDir
    Path exports;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final RecoveryBook ledger = new RecoveryBook();
    private final CapsuleVault vault = new CapsuleVault();
    private final HoldBook<String> hold = new HoldBook<>();
    private final IncubatorStore incubators = new IncubatorStore();
    private final PrivacyPrefs prefs = new PrivacyPrefs();

    private static CompanionRecord record(UUID owner, String name, HomePos home) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"), CompanionMode.FOLLOW, 100L, List.of(),
                7, Optional.of(name), Optional.empty(), Optional.empty(), 1L, Optional.of(home), new CompoundTag());
    }

    /** Seeds one player; every number in their positions is {@code base}-derived so it can be searched for. */
    private CompanionRecord seed(UUID player, String name, int base) {
        HomePos home = new HomePos(OVERWORLD, new BlockPos(base, base + 1, base + 2));
        CompanionRecord live = record(player, name, home);
        ledger.live(live, UUID.randomUUID(), new HomePos(OVERWORLD, new BlockPos(base + 3, 64, base + 4)), 0L, NOW);
        CompanionRecord capsuled = record(player, name + "-two", home);
        ledger.toCapsule(capsuled, 0L, NOW);
        vault.store(capsuled);
        hold.add(player, UUID.randomUUID(), "item-of-" + name, 0L, 100L, NOW);
        incubators.put(new HomePos(OVERWORLD, new BlockPos(base + 5, 60, base + 6)), new Incubation(player, 3000));
        prefs.set(player, PrivacyPrefs.Setting.NAMES, true, NOW);
        return live;
    }

    private JsonObject export(UUID subject) {
        CompanionExport.Sources sources = new CompanionExport.Sources(ledger, vault, hold, incubators,
                List.of(new CreaturePets.Pet(UUID.randomUUID(), "nerocreatures:glimmer", "follow", 2)), prefs);
        return CompanionExport.build(subject, sources, new CompanionExport.Settings(true, 180, 30),
                item -> new JsonPrimitive(String.valueOf(item)), NOW);
    }

    @Test
    void theExportHoldsEverythingOfTheSubjectAndNothingOfAnyoneElse() {
        CompanionRecord mine = seed(alice, "Rusty", 1_234_500);
        CompanionRecord theirs = seed(bob, "Bolt", 9_876_500);

        JsonObject document = export(alice);
        String text = CompanionExport.toJson(document);

        assertEquals(alice.toString(), document.get("subject").getAsString());
        assertEquals(2, document.getAsJsonArray("companions").size(), "both of the subject's ledger entries");
        assertEquals(1, document.getAsJsonArray("capsule_vault").size());
        assertEquals(1, document.getAsJsonArray("incubating_eggs").size());
        assertTrue(document.getAsJsonObject("privacy").get("names_hidden_from_others").getAsBoolean());
        assertTrue(text.contains(mine.companionId().toString()));
        assertTrue(text.contains("Rusty") && text.contains("Rusty-two"));
        assertTrue(text.contains("item-of-Rusty"), "the salvage hold");
        assertTrue(text.contains("1234503"), "the subject's own last-known position");

        assertFalse(text.contains(bob.toString()), "no other player's UUID");
        assertFalse(text.contains(theirs.companionId().toString()), "no other player's companion");
        assertFalse(text.contains("Bolt"), "no other player's companion name");
        for (int i = 0; i <= 6; i++) {
            assertFalse(text.contains(Integer.toString(9_876_500 + i)), "no other player's positions");
        }
    }

    @Test
    void aPlayerWithNoDataGetsAnEmptyButCompleteDocument() {
        seed(bob, "Bolt", 9_876_500);
        JsonObject document = export(alice);
        assertEquals(0, document.getAsJsonArray("companions").size());
        assertTrue(document.get("salvage_hold").isJsonNull());
        assertFalse(CompanionExport.toJson(document).contains(bob.toString()));
    }

    @Test
    void theFileIsWrittenInsideTheExportFolderDeletedOnRequestAndExpiresAfterAWeek() throws IOException {
        seed(alice, "Rusty", 1_234_500);
        String relative = CompanionExport.write(exports, alice, export(alice));
        assertEquals("nerocompanion/exports/" + alice + ".json", relative);
        assertTrue(CompanionExport.exists(exports, alice));
        assertThrows(IllegalStateException.class, () -> CompanionExport.file(exports, "../escape"));

        assertEquals(0, CompanionExport.purgeExpired(exports, System.currentTimeMillis()));
        Files.setLastModifiedTime(CompanionExport.file(exports, alice), FileTime.fromMillis(
                System.currentTimeMillis() - (CompanionExport.RETENTION_DAYS + 1) * 86_400_000L));
        assertEquals(1, CompanionExport.purgeExpired(exports, System.currentTimeMillis()));
        assertFalse(CompanionExport.exists(exports, alice));

        CompanionExport.write(exports, alice, export(alice));
        assertTrue(CompanionExport.delete(exports, alice));
        assertFalse(CompanionExport.exists(exports, alice));
    }

    @Test
    void theCooldownLimitsExportsAndAFailureRefundsIt() {
        ExportCooldown cooldown = new ExportCooldown();
        assertEquals(0L, cooldown.tryAcquire(alice, NOW, 300_000L));
        assertTrue(cooldown.tryAcquire(alice, NOW + 1_000L, 300_000L) > 0L);
        assertEquals(0L, cooldown.tryAcquire(bob, NOW + 1_000L, 300_000L), "per player");
        cooldown.refund(alice);
        assertEquals(0L, cooldown.tryAcquire(alice, NOW + 2_000L, 300_000L));
        assertEquals(0L, cooldown.tryAcquire(alice, NOW + 400_000L, 300_000L));
    }
}
