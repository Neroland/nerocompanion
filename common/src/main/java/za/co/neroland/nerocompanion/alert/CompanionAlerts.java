package za.co.neroland.nerocompanion.alert;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.link.CompanionLinkEvents;
import za.co.neroland.nerocompanion.privacy.CompanionPrivacy;

/**
 * The thin seam companion alerts go through: Core's per-player {@link LinkAlerts} (which NeroLink, when installed,
 * shows in the app), preceded by the rich link event that carries the push copy ({@link CompanionLinkEvents}).
 * The body comes from {@link AlertText}: type and name only.
 * Server thread. A failure to raise is swallowed (an alert is never worth a crash) and logged once without data.
 */
public final class CompanionAlerts {

    /** Where alerts go; tests swap it. */
    public interface Sink {
        void raise(MinecraftServer server, UUID owner, String id, String severity, String body);
    }

    private static final Sink CORE = (server, owner, id, severity, body) -> LinkAlerts.get(server).raise(server,
            owner, LinkAlert.raise(id, NeroCompanionCommon.MOD_ID, LinkAlert.Severity.valueOf(severity), body));

    private static final Consumer<LinkEvent> BUS = event -> NeroLinkRegistry.eventBus().publish(event);

    private static volatile Sink sink = CORE;
    private static volatile Consumer<LinkEvent> publisher = BUS;
    private static volatile boolean warned;

    private CompanionAlerts() {
    }

    /** Raises {@code kind} for {@code owner} about the companion {@code companionId} of {@code type}. */
    public static void raise(MinecraftServer server, UUID owner, AlertText.Kind kind, UUID companionId,
            Identifier type, Optional<String> name) {
        raise(server, owner, kind, companionId, type, name, OptionalInt.empty(), OptionalInt.empty());
    }

    /**
     * Raises {@code kind} through the link pipeline: the rich NeroLink event (with {@code _notify}) is published
     * first, unless the owner opted out of push or the same notice fell inside its 60-second window, then the Core
     * alert. {@code minutesLeft} is for {@code SALVAGE_HELD}, {@code level} for {@code BOND_LEVEL}.
     */
    public static void raise(MinecraftServer server, UUID owner, AlertText.Kind kind, UUID companionId,
            Identifier type, Optional<String> name, OptionalInt minutesLeft, OptionalInt level) {
        if (server == null || owner == null || companionId == null) {
            return;
        }
        try {
            CompanionLinkEvents.pipeline().fire(env(server),
                    new CompanionLinkEvents.Notice(kind, owner, companionId, type, name, minutesLeft, level));
        } catch (RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a companion alert could not be raised ({})",
                        failure.getClass().getName());
            }
        }
    }

    private static CompanionLinkEvents.Env env(MinecraftServer server) {
        return new CompanionLinkEvents.Env() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public boolean pushAllowed(UUID owner) {
                return CompanionPrivacy.pushAllowed(server, owner);
            }

            @Override
            public void publish(LinkEvent event) {
                publisher.accept(event);
            }

            @Override
            public void raiseAlert(UUID owner, String id, String severity, String body) {
                sink.raise(server, owner, id, severity, body);
            }
        };
    }

    /** Tests: capture published link events instead of Core's bus ({@code null} restores the default). */
    public static void usePublisher(Consumer<LinkEvent> replacement) {
        publisher = replacement == null ? BUS : replacement;
    }

    /** Tests: capture alerts instead of writing Core's store ({@code null} restores the default). */
    public static void useSink(Sink replacement) {
        sink = replacement == null ? CORE : replacement;
    }
}
