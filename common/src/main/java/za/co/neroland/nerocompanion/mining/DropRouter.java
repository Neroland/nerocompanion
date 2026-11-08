package za.co.neroland.nerocompanion.mining;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a mining bot's drops go, in a fixed order: its own cargo, then a nearby carry bot of the <em>same owner</em>,
 * then its bound container (only when {@code mayOpen} allows it, checked by the sink), then the owner. Pure and
 * generic over the stack type so the order is tested without a game. Whatever no stage takes drops at the bot
 * itself - never into or at another player.
 */
public final class DropRouter {

    /** The stages, in routing order. */
    public enum Stage {
        OWN_CARGO,
        CARRY_BOT,
        BOUND_CONTAINER,
        OWNER
    }

    public static final List<Stage> ORDER = List.of(Stage.OWN_CARGO, Stage.CARRY_BOT, Stage.BOUND_CONTAINER,
            Stage.OWNER);

    /** One destination. {@link #offer} returns what it did not take. */
    public interface Sink<S> {

        /** In reach and allowed right now (same owner, in range, {@code mayOpen} for a container...). */
        boolean available();

        S offer(S stack);
    }

    private DropRouter() {
    }

    /** Offers {@code stack} to each available sink in {@link #ORDER}; returns the leftover (empty if all taken). */
    public static <S> S route(S stack, Map<Stage, ? extends Sink<S>> sinks, Predicate<S> isEmpty) {
        S left = stack;
        for (Stage stage : ORDER) {
            if (isEmpty.test(left)) {
                break;
            }
            Sink<S> sink = sinks.get(stage);
            if (sink != null && sink.available()) {
                left = sink.offer(left);
            }
        }
        return left;
    }

    /**
     * Where a full bot walks to unload: the first stage after its own cargo that is reachable. Empty means
     * nowhere - the job pauses with "cargo full".
     */
    public static Optional<Stage> unloadTarget(Set<Stage> reachable) {
        for (Stage stage : ORDER) {
            if (stage != Stage.OWN_CARGO && reachable.contains(stage)) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }
}
