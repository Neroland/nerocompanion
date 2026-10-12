package za.co.neroland.nerocompanion.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.event.CoreEvents;
import za.co.neroland.nerolandcore.progression.GateScope;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * Listener for the <b>hook gates</b> a quest mod opens when a player completes one of NeroCompanion's
 * own quests ({@code data/nerocompanion/neroland_gates/hook/skin/<id>.json}, named by the pack's
 * {@code mod_hook} rewards). NeroCompanion never imports the quest mod: it only hears Core's
 * progression events.
 *
 * <p>{@code nerocompanion:hook/skin/<id>} unlocks the cosmetic companion skin {@code <id>} for that
 * player. The open gate <i>is</i> the unlock record, so it is <b>never closed</b> (a permanent unlock) and
 * no second store is needed: the future skin picker reads {@link #isSkinUnlocked} as a display check,
 * never an access check. Until companions exist the only visible effect is one chat line when the gate
 * opens for an online player; a gate that opened while the player was offline is simply open on their
 * return, which is the unlock itself, so nothing is replayed on login.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the gate change's target is the player's UUID string; it is used only to
 * find the online player, never logged or broadcast. The unlock lives in Core's gate store, which Core's
 * privacy document covers (export and erasure included); NeroCompanion stores nothing of its own.
 */
public final class QuestHooks {

    /** Path prefix shared by every skin hook gate. */
    public static final String SKIN_HOOK_PREFIX = "hook/skin/";

    /** The skins this jar ships a hook gate for. Keep in step with {@code neroland_gates/hook/skin/}. */
    public static final List<String> SKINS = List.of("aurora");

    /** The running server, captured from the server tick so the listener can resolve online players. */
    private static volatile MinecraftServer server;

    private QuestHooks() {
    }

    /** The hook gate that unlocks skin {@code id}: {@code nerocompanion:hook/skin/<id>}. */
    public static Identifier skinHook(String id) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, SKIN_HOOK_PREFIX + id);
    }

    /** Register the progression listener; call once from common init. */
    public static void init() {
        CoreEvents.onProgression(change -> {
            if (!change.open() || change.scope() != GateScope.PLAYER) {
                return;
            }
            String skin = skinOf(change.gate());
            if (skin == null) {
                return;
            }
            MinecraftServer running = server;
            if (running == null) {
                return; // no server yet: the open gate is the unlock, nothing to deliver
            }
            UUID id;
            try {
                id = UUID.fromString(change.target());
            } catch (IllegalArgumentException e) {
                return;
            }
            ServerPlayer player = running.getPlayerList().getPlayer(id);
            if (player == null) {
                return; // offline: the gate stays open and the skin shows as unlocked on return
            }
            announce(player, skin);
        });
    }

    /** Server tick: remember the server so the listener can find online players. Call from each loader. */
    public static void tick(MinecraftServer running) {
        server = running;
    }

    /** Display check for the skin picker: whether skin {@code id} is unlocked for this player. */
    public static boolean isSkinUnlocked(ServerPlayer player, String id) {
        return ProgressionGates.isOpen(player, skinHook(id));
    }

    /** The shipped skins that are unlocked for this player, in {@link #SKINS} order. */
    public static List<String> unlockedSkins(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (String skin : SKINS) {
            if (isSkinUnlocked(player, skin)) {
                out.add(skin);
            }
        }
        return out;
    }

    /** The skin id a hook gate names, or {@code null} when the gate is not one of this mod's skin hooks. */
    static String skinOf(Identifier gate) {
        if (!NeroCompanionCommon.MOD_ID.equals(gate.getNamespace()) || !gate.getPath().startsWith(SKIN_HOOK_PREFIX)) {
            return null;
        }
        String skin = gate.getPath().substring(SKIN_HOOK_PREFIX.length());
        return skin.isEmpty() ? null : skin;
    }

    /** The bounded placeholder effect: one chat line naming the skin. The skin itself lands with companions. */
    private static void announce(ServerPlayer player, String skin) {
        player.sendSystemMessage(Component.translatable("message.nerocompanion.skin_unlocked", skin));
    }
}
