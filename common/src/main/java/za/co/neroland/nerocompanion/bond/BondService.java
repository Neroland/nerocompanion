package za.co.neroland.nerocompanion.bond;

import java.util.concurrent.atomic.AtomicBoolean;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Applies earned bond points to a companion (server) and fires {@link BondEvents} on a level-up. The points live in
 * the companion's record ({@code CompanionRecord.bondPoints}); the entity exposes no setter in the frozen contract,
 * so the write goes through a {@link PointsWriter} the integration step installs (one line:
 * {@code BondService.useWriter(CompanionEntity::setBondPoints)}). Until then grants are dropped with one WARN.
 */
public final class BondService {

    /** Stores a companion's new bond points (and refreshes its synched level). */
    @FunctionalInterface
    public interface PointsWriter {
        void set(CompanionEntity companion, int points);
    }

    private static volatile PointsWriter writer;
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private BondService() {
    }

    public static void useWriter(PointsWriter pointsWriter) {
        writer = pointsWriter;
    }

    public static boolean hasWriter() {
        return writer != null;
    }

    /**
     * Adds {@code gain} points. Returns the new level if it rose, else -1. Level-up listeners run before return.
     */
    public static int grant(CompanionEntity companion, int gain) {
        if (gain <= 0) {
            return -1;
        }
        PointsWriter w = writer;
        if (w == null) {
            if (WARNED.compareAndSet(false, true)) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] bond points are not being saved: no bond writer is "
                        + "installed");
            }
            return -1;
        }
        int before = companion.bondPoints();
        int after = BondLevels.add(before, gain);
        if (after == before) {
            return -1;
        }
        w.set(companion, after);
        int from = BondLevels.level(before);
        int to = BondLevels.level(after);
        if (to > from) {
            BondEvents.fire(new BondEvents.LevelUp(companion, from, to));
            return to;
        }
        return -1;
    }
}
