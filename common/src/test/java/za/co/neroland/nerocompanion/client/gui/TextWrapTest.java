package za.co.neroland.nerocompanion.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

class TextWrapTest {

    /** One unit per character. */
    private static final ToIntFunction<String> CHARS = String::length;

    @Test
    void breaksOnlyBetweenWords() {
        assertEquals(List.of("None of your", "companions are", "close enough to", "park."),
                TextWrap.wrap("None of your companions are close enough to park.", CHARS, 15, 15, 15, 15));
    }

    @Test
    void lastLineGetsAnEllipsisWhenTextIsLeftOver() {
        assertEquals(List.of("None of your", "companions…"),
                TextWrap.wrap("None of your companions are close enough to park.", CHARS, 15, 15));
    }

    @Test
    void aWordWiderThanTheLineIsShortenedNotSplit() {
        assertEquals(List.of("Stand…"), TextWrap.wrap("Standard", CHARS, 6, 6));
        assertEquals(List.of("Bobble", "Beanie"), TextWrap.wrap("Bobble Beanie", CHARS, 7, 7));
    }

    @Test
    void perLineWidths() {
        assertEquals(List.of("A reward for finishing", "Companion"),
                TextWrap.wrap("A reward for finishing Companion", CHARS, 24, 10));
    }

    @Test
    void fitWordsKeepsWholeWords() {
        assertEquals("Nearby (within…", TextWrap.fitWords("Nearby (within 8 blocks)", CHARS, 16));
        assertEquals("Lost", TextWrap.fitWords("Lost", CHARS, 16));
        assertEquals("", TextWrap.fitWords("  ", CHARS, 16));
    }
}
