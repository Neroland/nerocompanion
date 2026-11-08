package za.co.neroland.nerocompanion.client;

/**
 * The one place per-type client code is switched on: {@link CompanionRenderer} layers and pose hooks, and
 * {@link ClientHooks} listeners. Each package exposes one static client {@code register()}; this class calls them
 * from client setup ({@code CompanionClient.init}), before any renderer is built. Client only.
 */
public final class ClientCompanionContent {

    private static boolean done;

    private ClientCompanionContent() {
    }

    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        za.co.neroland.nerocompanion.scout.client.ScoutClient.register();
        za.co.neroland.nerocompanion.client.emote.EmoteBubbles.register();
        za.co.neroland.nerocompanion.client.perch.PerchPose.register();
        za.co.neroland.nerocompanion.client.fx.CompanionFx.register();
        za.co.neroland.nerocompanion.client.item.CompanionItemProperties.register();
    }
}
