package za.co.neroland.nerocompanion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocompanion.ClientBridge;
import za.co.neroland.nerocompanion.client.screen.RemoteRadialScreen;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.item.RemoteItem;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * The client side of NeroCompanion: installs the {@link ClientBridge}, mirrors server snapshots into
 * {@link ClientState}, opens the Remote's radial, and handles the key mappings each client tick. Each loader's
 * client setup calls {@link #init()} and {@link #tick()}, and {@link ClientState#clear()} on disconnect.
 */
public final class CompanionClient implements ClientBridge {

    private static boolean radialPending;
    private static boolean radialOpenedThisUse;

    private CompanionClient() {
    }

    public static void init() {
        ClientBridge.install(new CompanionClient());
        ClientCompanionContent.init();
    }

    /** End of every client tick. */
    public static void tick() {
        GalleryCaptureHarness.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            radialPending = false;
            return;
        }
        ClientHooks.tick();
        if (!mc.player.isUsingItem()) {
            radialOpenedThisUse = false;
        }
        if (radialPending) {
            radialPending = false;
            if (Screens.current() == null) {
                if (mc.gameMode != null) {
                    mc.gameMode.releaseUsingItem(mc.player);
                }
                RemoteRadialScreen.open(targetUnderCrosshair());
            }
        }
        while (ClientKeys.HUD_TOGGLE.consumeClick()) {
            ClientState.toggleHud();
        }
        while (ClientKeys.OPEN_COMPANION.consumeClick()) {
            java.util.UUID target = targetUnderCrosshair();
            Payloads.HudEntry entry = ClientState.highlightedEntry();
            if (target == null && entry == null) {
                Screens.actionBar(Component.translatable("gui.nerocompanion.remote.none"));
            } else {
                Services.NETWORK.sendToServer(new Payloads.OpenCompanion(target != null ? target : entry.companionId()));
            }
        }
    }

    /** The companion of yours under the crosshair within 16 blocks, by its HUD entry. */
    private static java.util.UUID targetUnderCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.crosshairPickEntity;
        if (entity instanceof CompanionEntity && mc.player != null && entity.distanceToSqr(mc.player) <= 16.0D * 16.0D) {
            Payloads.HudEntry entry = ClientState.byEntityId(entity.getId());
            if (entry != null) {
                ClientState.highlight(entry.companionId());
                return entry.companionId();
            }
        }
        return null;
    }

    @Override
    public void remoteHeld(int ticks) {
        if (ticks >= RemoteItem.HOLD_TICKS && !radialOpenedThisUse) {
            radialOpenedThisUse = true;
            radialPending = true;
        }
    }

    @Override
    public void remoteTap() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.crosshairPickEntity;
        if (entity instanceof CompanionEntity) {
            Payloads.HudEntry entry = ClientState.byEntityId(entity.getId());
            if (entry == null) {
                Screens.actionBar(Component.translatable("gui.nerocompanion.reason.not_owner"));
            } else {
                ClientState.highlight(entry.companionId());
                Services.NETWORK.sendToServer(new Payloads.OpenCompanion(entry.companionId()));
            }
        } else if (ClientState.hud().isEmpty()) {
            Screens.actionBar(Component.translatable("gui.nerocompanion.remote.none"));
        }
    }

    @Override
    public void onHudSync(Payloads.HudSync payload) {
        ClientState.setHud(payload.entries());
    }

    @Override
    public void onCompanionSnapshot(Payloads.CompanionSnapshot payload) {
        ClientState.setCompanion(payload);
    }

    @Override
    public void onChatter(Payloads.Chatter payload) {
        ClientHooks.chatter(payload);
    }

    @Override
    public void onScoutMarks(Payloads.ScoutMarks payload) {
        ClientHooks.scoutMarks(payload);
    }

    @Override
    public void onBaySnapshot(Payloads.BaySnapshot payload) {
        ClientState.setBay(payload);
    }

    @Override
    public void onLookCatalogue(Payloads.LookCatalogue payload) {
        za.co.neroland.nerocompanion.client.look.ClientLooks.accept(payload.looks());
    }

    @Override
    public void onImpulse(int entityId, int impulse) {
        za.co.neroland.nerocompanion.client.model.AnimTracks.impulse(entityId, impulse);
    }
}
