package za.co.neroland.nerocompanion.bond;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Bond level-up listeners (server thread). The seam for Stage 6 quest/milestone hooks ("bond level 3") and the
 * Stage 8 link event {@code BOND_LEVEL}. A listener gets the companion and the levels only; anything it forwards
 * must stay owner-scoped and carry no position.
 */
public final class BondEvents {

    /** A companion's bond level rose from {@code from} to {@code to} ({@code to > from}). */
    public record LevelUp(CompanionEntity companion, int from, int to) {
    }

    private static final List<Consumer<LevelUp>> LISTENERS = new CopyOnWriteArrayList<>();

    private BondEvents() {
    }

    public static void onLevelUp(Consumer<LevelUp> listener) {
        LISTENERS.add(java.util.Objects.requireNonNull(listener));
    }

    static void fire(LevelUp event) {
        for (Consumer<LevelUp> listener : LISTENERS) {
            listener.accept(event);
        }
    }
}
