package za.co.neroland.nerocompanion.compat.nerofactions;

import java.util.Collections;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * The shared-faction comparison, kept free of NeroFactions types so it can be tested on a Core-only classpath. Two
 * players share a faction when their membership sets overlap; a missing or empty set never matches.
 */
final class FactionOverlap {

    private FactionOverlap() {
    }

    static boolean shareFaction(@Nullable Set<?> mine, @Nullable Set<?> theirs) {
        return mine != null && theirs != null && !mine.isEmpty() && !theirs.isEmpty()
                && !Collections.disjoint(mine, theirs);
    }
}
