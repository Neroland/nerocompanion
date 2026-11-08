package za.co.neroland.nerocompanion.bond;

/**
 * Bond levels 0-4 from bond points. Levels unlock cosmetics only - never stats:
 *
 * <pre>
 * Level  Points  ~Time (near the owner only)  Unlocks (010 section 4.5, 005 section 3.4)
 *   0        0   -                            plain nameplate
 *   1      100   ~33 min                      "look around" idle
 *   2      300   ~1 h 40 min                  second trait idle, happy greeting
 *   3      700   ~4 h                         trait trick, heart before the name
 *   4     1500   ~8 h 20 min                  glowing nameplate
 * </pre>
 *
 * Time assumes presence only ({@link BondRules#POINTS_PER_PRESENCE} per {@link BondRules#PRESENCE_SECONDS} s);
 * pets and finished jobs speed it up, capped by their cooldowns. The thresholds equal
 * {@code entity.Bond}'s, which the entity uses for the synched level; a test keeps the two in step.
 */
public final class BondLevels {

    public static final int MAX_LEVEL = 4;
    /** Points needed for levels 1-4. */
    private static final int[] THRESHOLDS = {100, 300, 700, 1500};
    /** Points are stored up to this (well past level 4) so a long-lived companion never overflows. */
    public static final int MAX_POINTS = 1_000_000;

    private BondLevels() {
    }

    public static int level(int points) {
        int level = 0;
        for (int threshold : THRESHOLDS) {
            if (points >= threshold) {
                level++;
            }
        }
        return level;
    }

    /** Points needed to reach {@code level} (0 for level 0). */
    public static int threshold(int level) {
        if (level <= 0) {
            return 0;
        }
        return THRESHOLDS[Math.min(level, MAX_LEVEL) - 1];
    }

    /** Points still needed for the next level, or -1 at the top level. */
    public static int pointsToNext(int points) {
        for (int threshold : THRESHOLDS) {
            if (points < threshold) {
                return threshold - points;
            }
        }
        return -1;
    }

    /** {@code points + gain}, clamped to {@code 0..MAX_POINTS}. */
    public static int add(int points, int gain) {
        long sum = (long) points + gain;
        return (int) Math.max(0L, Math.min(MAX_POINTS, sum));
    }
}
