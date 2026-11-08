package za.co.neroland.nerocompanion.ai;

/**
 * The rows of the {@link ModeGoalTable} priority table. Lower priority runs first; goals of equal priority are
 * mutually exclusive through their flags. {@link ModeGoalTable} says which slots a mode installs and
 * {@link ModeController} turns slots into goal instances. Kept free of game classes so the table is unit-tested.
 */
public enum GoalSlot {
    FLOAT(0),
    SLEEP(1),
    RETURN_HOME(2),
    PERCH(3),
    WORK(4),
    FOLLOW(5),
    STAY(6),
    TRAIT_IDLE(7),
    WANDER(8),
    LOOK(9);

    private final int priority;

    GoalSlot(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return this.priority;
    }
}
