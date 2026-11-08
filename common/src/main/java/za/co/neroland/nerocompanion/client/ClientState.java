package za.co.neroland.nerocompanion.client;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * The client's mirror of what the server told this player about their own companions: the HUD list, the open
 * Companion screen's snapshot and the open Bay's snapshot, each with a version counter screens poll. Cleared on
 * disconnect. Client only; holds nothing about anyone else.
 */
public final class ClientState {

    private static List<Payloads.HudEntry> hud = List.of();
    private static int highlighted;
    @Nullable
    private static Payloads.CompanionSnapshot companion;
    private static int companionVersion;
    @Nullable
    private static Payloads.BaySnapshot bay;
    private static int bayVersion;
    private static boolean hudHidden;

    private ClientState() {
    }

    public static void clear() {
        hud = List.of();
        highlighted = 0;
        companion = null;
        bay = null;
        companionVersion++;
        bayVersion++;
        za.co.neroland.nerocompanion.client.look.ClientLooks.clear();
        za.co.neroland.nerocompanion.client.fx.CompanionFx.clear();
    }

    public static List<Payloads.HudEntry> hud() {
        return hud;
    }

    static void setHud(List<Payloads.HudEntry> entries) {
        hud = List.copyOf(entries);
        if (highlighted >= hud.size()) {
            highlighted = 0;
        }
    }

    /** The HUD-highlighted (radial target) companion, or null. */
    @Nullable
    public static Payloads.HudEntry highlightedEntry() {
        return hud.isEmpty() ? null : hud.get(Math.min(highlighted, hud.size() - 1));
    }

    public static int highlightedIndex() {
        return hud.isEmpty() ? -1 : Math.min(highlighted, hud.size() - 1);
    }

    public static void highlight(UUID companionId) {
        for (int i = 0; i < hud.size(); i++) {
            if (hud.get(i).companionId().equals(companionId)) {
                highlighted = i;
                return;
            }
        }
    }

    /** Moves the highlight to the next (or previous) LOADED companion. */
    public static void cycle(int direction) {
        if (hud.isEmpty()) {
            return;
        }
        int n = hud.size();
        for (int step = 1; step <= n; step++) {
            int next = Math.floorMod(highlighted + direction * step, n);
            if (hud.get(next).has(Payloads.HudEntry.LOADED)) {
                highlighted = next;
                return;
            }
        }
    }

    @Nullable
    public static Payloads.HudEntry byEntityId(int entityId) {
        for (Payloads.HudEntry entry : hud) {
            if (entry.entityId() == entityId && entityId >= 0) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public static Payloads.CompanionSnapshot companion() {
        return companion;
    }

    public static int companionVersion() {
        return companionVersion;
    }

    static void setCompanion(Payloads.CompanionSnapshot snapshot) {
        companion = snapshot;
        companionVersion++;
    }

    @Nullable
    public static Payloads.BaySnapshot bay() {
        return bay;
    }

    public static int bayVersion() {
        return bayVersion;
    }

    static void setBay(Payloads.BaySnapshot snapshot) {
        bay = snapshot;
        bayVersion++;
    }

    public static boolean hudHidden() {
        return hudHidden;
    }

    public static void toggleHud() {
        hudHidden = !hudHidden;
    }
}
