package za.co.neroland.nerocompanion;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * The one door from common code into the client: common classes (items, payload handlers) call through this
 * interface and never name a client class, so nothing client-only loads on a dedicated server. The physical client
 * installs the real implementation at client setup; until then (and on a server) every call is a no-op.
 */
public interface ClientBridge {

    ClientBridge NONE = new ClientBridge() { };

    /** The Remote's use key has been held for {@code ticks} ticks (the radial opens at 4). */
    default void remoteHeld(int ticks) {
    }

    /** The Remote was tapped (released before 4 ticks). */
    default void remoteTap() {
    }

    default void onHudSync(Payloads.HudSync payload) {
    }

    default void onCompanionSnapshot(Payloads.CompanionSnapshot payload) {
    }

    default void onBaySnapshot(Payloads.BaySnapshot payload) {
    }

    default void onChatter(Payloads.Chatter payload) {
    }

    default void onScoutMarks(Payloads.ScoutMarks payload) {
    }

    default void onLookCatalogue(Payloads.LookCatalogue payload) {
    }

    /** A companion's one-shot animation impulse ({@code CompanionEntity.IMPULSE_*}) arrived as an entity event. */
    default void onImpulse(int entityId, int impulse) {
    }

    final class Holder {
        private static volatile ClientBridge current = NONE;

        private Holder() {
        }
    }

    static ClientBridge get() {
        return Holder.current;
    }

    /** Client setup only. */
    static void install(ClientBridge bridge) {
        Holder.current = bridge == null ? NONE : bridge;
    }
}
