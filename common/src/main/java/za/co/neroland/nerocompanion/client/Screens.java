package za.co.neroland.nerocompanion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/** Per-line seams for the client screen stack and the action bar. Client only. */
public final class Screens {

    private Screens() {
    }

    /** The open screen, or null. */
    @Nullable
    public static Screen current() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }

    /** Shows {@code screen} ({@code null}: back to the game). */
    public static void set(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }

    /** Whether the player hid the HUD (F1). */
    public static boolean guiHidden() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        return mc.gui.hud.isHidden();
        //?} else {
        /*return mc.options.hideGui;
        *///?}
    }

    /** One action-bar sentence for this player only. */
    public static void actionBar(Component text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        //? if >=26.1 {
        player.sendOverlayMessage(text);
        //?} else {
        /*player.displayClientMessage(text, true);
        *///?}
    }
}
