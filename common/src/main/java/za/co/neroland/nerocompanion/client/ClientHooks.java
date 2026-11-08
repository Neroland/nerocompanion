package za.co.neroland.nerocompanion.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * Client listener lists for packages that own no client entry point: chatter (emote bubbles), scout marks, and a
 * per-tick callback (end of client tick, only while a player is in a world). Register from client init (see
 * {@link ClientCompanionContent}); listeners run on the client thread. Client only.
 */
public final class ClientHooks {

    private static final List<Consumer<Payloads.Chatter>> CHATTER = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Payloads.ScoutMarks>> SCOUT_MARKS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> TICK = new CopyOnWriteArrayList<>();

    private ClientHooks() {
    }

    public static void onChatter(Consumer<Payloads.Chatter> listener) {
        CHATTER.add(java.util.Objects.requireNonNull(listener));
    }

    public static void onScoutMarks(Consumer<Payloads.ScoutMarks> listener) {
        SCOUT_MARKS.add(java.util.Objects.requireNonNull(listener));
    }

    public static void onTick(Runnable listener) {
        TICK.add(java.util.Objects.requireNonNull(listener));
    }

    static void chatter(Payloads.Chatter payload) {
        for (Consumer<Payloads.Chatter> listener : CHATTER) {
            listener.accept(payload);
        }
    }

    static void scoutMarks(Payloads.ScoutMarks payload) {
        for (Consumer<Payloads.ScoutMarks> listener : SCOUT_MARKS) {
            listener.accept(payload);
        }
    }

    static void tick() {
        for (Runnable listener : TICK) {
            listener.run();
        }
    }
}
