package za.co.neroland.nerocompanion.network;

import java.util.Optional;

/**
 * The ownership and state gate every companion intent passes (pure): the companion must exist and be LIVE, the
 * sender must be its owner, it must not be inert, and screen actions (chips, rename, looks) need it within the
 * screen range. Returns the reason lang key of the first failure, or empty when the action may proceed.
 */
public final class ActionGate {

    private ActionGate() {
    }

    public static Optional<String> check(boolean found, boolean owner, boolean inert, boolean needsScreenRange,
            boolean inScreenRange) {
        if (!found) {
            return Optional.of("gui.nerocompanion.reason.not_live");
        }
        if (!owner) {
            return Optional.of("gui.nerocompanion.reason.not_owner");
        }
        if (inert) {
            return Optional.of("gui.nerocompanion.reason.not_live");
        }
        if (needsScreenRange && !inScreenRange) {
            return Optional.of("gui.nerocompanion.reason.away");
        }
        return Optional.empty();
    }
}
