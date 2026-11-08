package za.co.neroland.nerocompanion.client.gui;

/**
 * Number and duration formatting for the screens (pure): FE with a thin-space thousands separator, minutes rounded,
 * an hour and more as "h min", under a minute in words. The words themselves are lang keys; this returns the
 * arguments and which key to use.
 */
public final class UiFormat {

    /** U+2009 thin space. */
    public static final char THIN_SPACE = ' ';

    private UiFormat() {
    }

    /** {@code 18400} -> {@code "18 400"} (thin space). */
    public static String fe(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        int lead = digits.length() % 3;
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) {
                out.append(THIN_SPACE);
            }
            out.append(digits.charAt(i));
        }
        return value < 0 ? "-" + out : out.toString();
    }

    /** A duration in whole minutes as a lang key + arguments. */
    public record Duration(String key, Object[] args) {
    }

    public static Duration duration(double minutes) {
        if (minutes < 1.0D) {
            return new Duration("gui.nerocompanion.time.under_minute", new Object[0]);
        }
        long rounded = Math.round(minutes);
        if (rounded >= 60L) {
            return new Duration("gui.nerocompanion.time.hours", new Object[] {rounded / 60L, rounded % 60L});
        }
        return new Duration("gui.nerocompanion.time.min", new Object[] {rounded});
    }

    /** Fill width for a bar of {@code innerWidth} at {@code pct}. */
    public static int fill(int pct, int innerWidth) {
        int clamped = Math.max(0, Math.min(100, pct));
        return (int) Math.round(clamped / 100.0D * innerWidth);
    }
}
