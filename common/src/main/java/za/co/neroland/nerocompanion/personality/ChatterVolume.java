package za.co.neroland.nerocompanion.personality;

/**
 * How talkative a companion is, from its cosmetic chips ({@code 017}): Chatterbox doubles chatter, Hush cuts it to
 * a quarter, Silent Running stops it. Chatterbox and Hush are an exclusive pair; if both somehow appear Hush wins
 * (quieter is the safer reading). Pure.
 */
public enum ChatterVolume {
    SILENT(0, 1),
    HUSHED(1, 4),
    NORMAL(1, 1),
    CHATTY(2, 1);

    private final int numerator;
    private final int denominator;

    ChatterVolume(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static ChatterVolume of(boolean silentRunning, boolean chatterbox, boolean hush) {
        if (silentRunning) {
            return SILENT;
        }
        if (hush) {
            return HUSHED;
        }
        return chatterbox ? CHATTY : NORMAL;
    }

    /** Frequency relative to normal (2.0 chatty, 0.25 hushed, 0 silent). */
    public double frequency() {
        return (double) this.numerator / this.denominator;
    }

    public boolean silent() {
        return this == SILENT;
    }

    /** {@code base} cooldown scaled by the inverse of the frequency; {@link Integer#MAX_VALUE} when silent. */
    public int scaleCooldown(int base) {
        if (this.silent()) {
            return Integer.MAX_VALUE;
        }
        long scaled = (long) Math.max(0, base) * this.denominator / this.numerator;
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    /** {@code chance} (0..1) scaled by the frequency, clamped to 0..1. */
    public double scaleChance(double chance) {
        return Math.max(0.0D, Math.min(1.0D, chance * this.frequency()));
    }
}
