package za.co.neroland.nerocompanion.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Rename goes through formatting removal, the 24-character cap and the server's text filter (a fake filter here);
 * the ownership gate refuses a non-owner before anything else; the rate limiter drops the excess.
 */
class RenameAndGateTest {

    private static final RenameRules.Verdict CLEAN = text -> false;
    private static final RenameRules.Verdict NO_BADWORD = text -> text.toLowerCase().contains("badword");

    @Test
    void capsAtTwentyFourCharacters() {
        RenameRules.Result result = RenameRules.decide(Optional.empty(), "abcdefghijklmnopqrstuvwxyz0123", CLEAN);
        assertEquals(RenameRules.Outcome.RENAMED, result.outcome());
        assertEquals("abcdefghijklmnopqrstuvwx", result.name().orElseThrow());
        assertEquals(24, result.name().orElseThrow().length());
    }

    @Test
    void stripsFormattingAndControlCharacters() {
        assertEquals("Pip", RenameRules.clean("§cP§li\u0007p"));
        assertEquals("Pip", RenameRules.clean("  Pip  §"));
    }

    @Test
    void stripsUnicodeFormatAndBidiCharactersAndNeedsALetterOrDigit() {
        assertEquals("Pip", RenameRules.clean("P\u200Bi\u200Dp\uFEFF"));
        assertEquals("Pip", RenameRules.clean("\u202EPip\u202C"));
        assertEquals("Pip Two", RenameRules.clean("Pip \u00A0\u3000  Two"));
        assertEquals("", RenameRules.clean("\u200B\u200B\u200B"));
        assertEquals("", RenameRules.clean("--- ..."));
        assertEquals(RenameRules.Outcome.CLEARED,
                RenameRules.decide(Optional.of("Pip"), "\u200B\u200B\u200B", CLEAN).outcome());
        String zeroWidthPadded = "\u200B".repeat(30) + "Pip";
        assertEquals("Pip", RenameRules.decide(Optional.empty(), zeroWidthPadded, CLEAN).name().orElseThrow());
    }

    @Test
    void theFilterDecidesAfterTheCap() {
        RenameRules.Result filtered = RenameRules.decide(Optional.of("Pip"), "my badword bot", NO_BADWORD);
        assertEquals(RenameRules.Outcome.FILTERED, filtered.outcome());
        assertTrue(filtered.name().isEmpty());
        // text past the cap never reaches the filter, so it cannot be what gets a name refused
        RenameRules.Result beyond = RenameRules.decide(Optional.empty(), "abcdefghijklmnopqrstuvwxbadword", NO_BADWORD);
        assertEquals(RenameRules.Outcome.RENAMED, beyond.outcome());
        int[] calls = {0};
        RenameRules.decide(Optional.empty(), "Pip", text -> {
            calls[0]++;
            assertEquals("Pip", text);
            return false;
        });
        assertEquals(1, calls[0]);
    }

    @Test
    void sameNameAndClearing() {
        assertEquals(RenameRules.Outcome.SAME, RenameRules.decide(Optional.of("Pip"), " Pip ", CLEAN).outcome());
        assertEquals(RenameRules.Outcome.CLEARED, RenameRules.decide(Optional.of("Pip"), "   ", CLEAN).outcome());
        assertEquals(RenameRules.Outcome.SAME, RenameRules.decide(Optional.empty(), "", CLEAN).outcome());
        assertEquals("gui.nerocompanion.reason.filtered", RenameRules.Outcome.FILTERED.key());
    }

    @Test
    void ownershipIsCheckedBeforeStateAndRange() {
        assertEquals("gui.nerocompanion.reason.not_owner", ActionGate.check(true, false, false, true, true).orElseThrow());
        assertEquals("gui.nerocompanion.reason.not_owner", ActionGate.check(true, false, true, true, false).orElseThrow());
        assertEquals("gui.nerocompanion.reason.not_live", ActionGate.check(false, false, false, false, false).orElseThrow());
        assertEquals("gui.nerocompanion.reason.not_live", ActionGate.check(true, true, true, false, true).orElseThrow());
        assertEquals("gui.nerocompanion.reason.away", ActionGate.check(true, true, false, true, false).orElseThrow());
        assertTrue(ActionGate.check(true, true, false, false, false).isEmpty());
        assertTrue(ActionGate.check(true, true, false, true, true).isEmpty());
    }

    @Test
    void rateLimiterDropsTheEleventhPayloadInASecondPerType() {
        RateLimiter limiter = new RateLimiter();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < RateLimiter.PER_SECOND; i++) {
            assertTrue(limiter.allow(player, "set_mode", 100L));
        }
        assertFalse(limiter.allow(player, "set_mode", 105L));
        assertTrue(limiter.allow(player, "chip_edit", 105L));
        assertTrue(limiter.allow(UUID.randomUUID(), "set_mode", 105L));
        assertTrue(limiter.allow(player, "set_mode", 120L));
    }
}
