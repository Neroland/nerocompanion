package za.co.neroland.nerocompanion.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.client.gui.UiFormat;

/** Screen number formatting: thin-space thousands, rounded minutes, hours, and the bar fill rule. */
class UiFormatTest {

    @Test
    void feUsesAThinSpaceThousandsSeparator() {
        assertEquals("18 400", UiFormat.fe(18_400L));
        assertEquals("200 000", UiFormat.fe(200_000L));
        assertEquals("950", UiFormat.fe(950L));
        assertEquals("1 000 000", UiFormat.fe(1_000_000L));
        assertEquals("0", UiFormat.fe(0L));
    }

    @Test
    void durations() {
        assertEquals("gui.nerocompanion.time.under_minute", UiFormat.duration(0.4D).key());
        assertEquals("gui.nerocompanion.time.min", UiFormat.duration(34.4D).key());
        assertArrayEquals(new Object[] {34L}, UiFormat.duration(34.4D).args());
        assertEquals("gui.nerocompanion.time.hours", UiFormat.duration(100.0D).key());
        assertArrayEquals(new Object[] {1L, 40L}, UiFormat.duration(100.0D).args());
    }

    @Test
    void barFill() {
        assertEquals(0, UiFormat.fill(0, 110));
        assertEquals(110, UiFormat.fill(100, 110));
        assertEquals(55, UiFormat.fill(50, 110));
        assertEquals(110, UiFormat.fill(140, 110));
    }
}
