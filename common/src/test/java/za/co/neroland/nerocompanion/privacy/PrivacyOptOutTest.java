package za.co.neroland.nerocompanion.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;

/**
 * The opt-outs ({@code privacy.allowNames} and each player's names / chatter / push settings) and the Capsule-name
 * rule after an erasure.
 */
class PrivacyOptOutTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void namesAreShownOnlyWhenTheServerAllowsAndTheOwnerHasNotOptedOut() {
        PrivacyPrefs prefs = new PrivacyPrefs();
        assertTrue(CompanionPrivacy.namesShown(true, prefs, owner), "on by default");
        assertFalse(CompanionPrivacy.namesShown(false, prefs, owner), "the server switch hides every name");

        prefs.set(owner, PrivacyPrefs.Setting.NAMES, true, 1L);
        assertFalse(CompanionPrivacy.namesShown(true, prefs, owner), "the owner's own opt-out");
        assertTrue(CompanionPrivacy.namesShown(true, prefs, other), "per player");

        prefs.set(owner, PrivacyPrefs.Setting.NAMES, false, 2L);
        assertTrue(CompanionPrivacy.namesShown(true, prefs, owner), "and back on");
    }

    @Test
    void eachSettingIsIndependentAndStampsTheActivityClock() {
        PrivacyPrefs prefs = new PrivacyPrefs();
        prefs.set(owner, PrivacyPrefs.Setting.CHATTER, true, 10L);
        prefs.set(owner, PrivacyPrefs.Setting.PUSH, true, 20L);
        assertTrue(prefs.isOff(owner, PrivacyPrefs.Setting.CHATTER));
        assertTrue(prefs.isOff(owner, PrivacyPrefs.Setting.PUSH));
        assertFalse(prefs.isOff(owner, PrivacyPrefs.Setting.NAMES));
        assertEquals(Optional.of(20L), prefs.lastSeen(owner));
        assertFalse(prefs.isOff(other, PrivacyPrefs.Setting.PUSH));
        assertFalse(prefs.holdsFor(other), "no row is written for a player who changed nothing");
        assertTrue(prefs.forget(owner));
        assertFalse(prefs.isOff(owner, PrivacyPrefs.Setting.CHATTER), "erasure resets to the defaults");
    }

    @Test
    void aHiddenNameIsDecidedBeforeTheRecordIsPublished() {
        // Deploy and Bay unpark run on the server thread: the owner's choice is read at once.
        assertTrue(CompanionPrivacy.hideOnApply(true, true, () -> false), "deploy, names off: hidden from the start");
        assertFalse(CompanionPrivacy.hideOnApply(true, true, () -> true), "deploy, names on: shown");
        // A chunk load that does not run on the server thread: hidden until the first evaluation decides.
        assertTrue(CompanionPrivacy.hideOnApply(true, false, () -> true), "load off-thread: hidden, never flashed");
        // Server thread load, names off.
        assertTrue(CompanionPrivacy.hideOnApply(true, true, () -> {
            PrivacyPrefs prefs = new PrivacyPrefs();
            prefs.set(owner, PrivacyPrefs.Setting.NAMES, true, 1L);
            return CompanionPrivacy.namesShown(true, prefs, owner);
        }), "load on the server thread, names off: hidden");
        assertTrue(CompanionPrivacy.hideOnApply(true, true, () -> {
            throw new IllegalStateException("store unavailable");
        }), "when in doubt, hidden");
        assertFalse(CompanionPrivacy.hideOnApply(false, false, () -> false), "the client never decides");
    }

    private static CompanionRecord capsule(UUID id, Optional<String> name, int bond) {
        return new CompanionRecord(CompanionRecord.SCHEMA, id, CompanionRecord.NO_OWNER,
                Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"), CompanionMode.FOLLOW, 100L, List.of(),
                bond, name, Optional.empty(), Optional.empty(), 1L, Optional.empty(), new CompoundTag());
    }

    @Test
    void aCapsuleOfAnErasedOwnersCompanionLosesItsNameAndBondOthersAreUntouched() {
        UUID erased = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        Set<UUID> tombstones = Set.of(erased);

        Optional<CompanionRecord> scrubbed = CompanionPrivacy.scrubbedCapsule(capsule(erased, Optional.of("Rusty"),
                120), tombstones::contains);
        assertTrue(scrubbed.isPresent());
        assertTrue(scrubbed.get().name().isEmpty(), "the name the erased owner wrote is gone");
        assertEquals(0, scrubbed.get().bondPoints());
        assertEquals(CompanionRecord.NO_OWNER, scrubbed.get().ownerId());
        assertEquals(erased, scrubbed.get().companionId(), "chips, looks and identity stay with the item");

        assertTrue(CompanionPrivacy.scrubbedCapsule(capsule(kept, Optional.of("Bolt"), 50), tombstones::contains)
                .isEmpty(), "a Capsule of a companion nobody erased is never touched");
        assertTrue(CompanionPrivacy.scrubbedCapsule(capsule(erased, Optional.empty(), 0), tombstones::contains)
                .isEmpty(), "an already ownerless, nameless Capsule needs no change");
    }
}
