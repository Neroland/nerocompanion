package za.co.neroland.nerocompanion.compat.nerotech;

import za.co.neroland.nerocompanion.compat.SiblingCompat;

/**
 * Nerotech — a documented no-op seam (decision 020). Nerotech ships no wireless <i>charging</i> node: its wireless
 * node is a paired point-to-point power link between two blocks, not a charger for players or entities. Nothing is
 * lost: Charging Pads and the Companion Bay already take Nerotech (and NeroPower) power and standard FE from any
 * neighbour through Core's {@code EnergyLookup} and an explicit {@code SideConfig} preset (decision 003), so this
 * façade compiles against nothing and imports nothing from Nerotech.
 */
public final class NerotechCompat {

    public static final String MOD_ID = "nerotech";

    private NerotechCompat() {
    }

    /** Whether Nerotech is installed (informational only: no behaviour depends on it). */
    public static boolean isLoaded() {
        return SiblingCompat.isLoaded(MOD_ID);
    }

    /** Always false: Nerotech has no wireless charging node to hand companions to. */
    public static boolean wirelessChargingNode() {
        return false;
    }
}
