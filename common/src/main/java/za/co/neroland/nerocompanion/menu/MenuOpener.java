package za.co.neroland.nerocompanion.menu;

import java.util.OptionalInt;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.telemetry.NeroCompanionTelemetry;

/**
 * The single door every NeroCompanion screen goes through: {@code openMenu}, but it cannot take the server thread
 * with it when the host platform misbehaves (a Bukkit/Paper hybrid's inventory-open bridge choking on a modded
 * menu). Catch {@link RuntimeException} / {@link LinkageError} only, put the player back in a clean state, tell
 * them why, log once and report it anonymously. Hybrid servers are not a supported platform.
 *
 * <p>Privacy: the log line and the telemetry report name the menu by our own translation key (or class name),
 * never the rendered title - a companion screen's title carries a player-chosen name.
 */
public final class MenuOpener {

    private MenuOpener() {
    }

    /** Opens {@code provider}; empty when the platform (or vanilla) refused. */
    public static OptionalInt open(ServerPlayer player, MenuProvider provider) {
        try {
            return player.openMenu(provider);
        } catch (RuntimeException | LinkageError failure) {
            recover(player, provider, failure);
            return OptionalInt.empty();
        }
    }

    /** {@link #open} as an interaction result: SUCCESS when the menu opened, CONSUME (spent, nothing) otherwise. */
    public static InteractionResult openOrConsume(ServerPlayer player, MenuProvider provider) {
        if (open(player, provider).isPresent()) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    private static void recover(ServerPlayer player, MenuProvider provider, Throwable cause) {
        String menu = describe(provider);
        NeroCompanionCommon.LOGGER.warn("[NeroCompanion] The server platform refused to open menu '{}' ({}); the "
                + "interaction was cancelled instead of crashing the server thread. Hybrid (Bukkit/Paper) servers "
                + "are not supported.", menu, cause.getClass().getName());
        try {
            player.closeContainer();
        } catch (RuntimeException | LinkageError ignored) {
            // The client resyncs on its own.
        }
        try {
            player.sendSystemMessage(Component.translatable("gui.nerocompanion.menu_open_failed"));
        } catch (RuntimeException | LinkageError ignored) {
            // The player simply does not get the notice.
        }
        NeroCompanionTelemetry.captureHandledException(cause, "menu_open", menu);
    }

    /** A label safe to log and report: our translation key, else the provider class. Never the rendered title. */
    private static String describe(MenuProvider provider) {
        try {
            Component title = provider.getDisplayName();
            if (title != null && title.getContents() instanceof TranslatableContents translatable) {
                return translatable.getKey();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to the class name.
        }
        return provider.getClass().getName();
    }
}
