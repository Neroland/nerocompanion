package za.co.neroland.nerocompanion.scheduler;

/**
 * The scheduler's verdict for one companion until its next evaluation.
 *
 * @param state  how often the AI runs
 * @param reason why (shown by debug tooling; never contains player data)
 */
public record Throttle(State state, Reason reason) {

    public enum State {
        /** AI every tick. */
        FULL,
        /** AI every 4th tick (32 < distance <= 64). */
        QUARTER,
        /** Forced SLEEP: no goals, no drain. */
        SLEEP
    }

    public enum Reason {
        NEAR_OWNER,
        WORKING,
        THROTTLED_DISTANCE,
        OWNER_OFFLINE,
        OWNER_OTHER_DIMENSION,
        TOO_FAR,
        NO_ENERGY,
        LOW_ENERGY_NO_HOME,
        NO_OWNER,
        /** Gathering the owner's death drops while the owner is elsewhere: only the salvage goal runs. */
        SALVAGING,
        /** Off-world radiation and no Rad Shielding chip (Nerospace façade). */
        HAZARD
    }

    public boolean sleeping() {
        return this.state == State.SLEEP;
    }
}
