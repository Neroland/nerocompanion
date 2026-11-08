package za.co.neroland.nerocompanion.scout;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

/**
 * Who receives a drone's scout marks and danger alerts: <b>the owner only</b>, resolved by the stored owner UUID among
 * the players of the drone's own level, and only if that player is eligible (a real player, not a loader fake
 * player). Never any other viewer, never a nearby player, never a broadcast. Pure.
 */
public final class MarkRouting {

    private MarkRouting() {
    }

    public static <V> Optional<V> recipient(@Nullable UUID owner, Iterable<? extends V> playersInLevel,
            Function<? super V, UUID> idOf, Predicate<? super V> eligible) {
        if (owner == null) {
            return Optional.empty();
        }
        for (V player : playersInLevel) {
            if (owner.equals(idOf.apply(player))) {
                return eligible.test(player) ? Optional.of(player) : Optional.empty();
            }
        }
        return Optional.empty();
    }
}
