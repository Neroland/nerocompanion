package za.co.neroland.nerocompanion.ai.drone;

/**
 * A companion class that owns a {@link DroneLamp}, so the light is cleaned up from {@code onLeaveWorld} as well as
 * from the lamp goal's {@code stop()}. The lamp work goal does nothing on a class without one.
 */
public interface LampCarrier {
    DroneLamp lamp();
}
