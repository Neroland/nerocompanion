package za.co.neroland.nerocompanion.scout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Marks and alerts go to the owner only - never another player, never a fake player, never anyone if absent. */
class MarkRoutingTest {

    private record Viewer(UUID id, boolean fake) {
    }

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static Optional<Viewer> route(UUID owner, List<Viewer> viewers) {
        return MarkRouting.recipient(owner, viewers, Viewer::id, v -> !v.fake());
    }

    @Test
    void onlyTheOwnerAmongManyPlayers() {
        Viewer owner = new Viewer(OWNER, false);
        Optional<Viewer> to = route(OWNER, List.of(new Viewer(OTHER, false), owner,
                new Viewer(UUID.randomUUID(), false)));
        assertEquals(Optional.of(owner), to);
    }

    @Test
    void nobodyWhenTheOwnerIsAbsentOrUnknown() {
        assertTrue(route(OWNER, List.of(new Viewer(OTHER, false))).isEmpty());
        assertTrue(route(null, List.of(new Viewer(OTHER, false), new Viewer(OWNER, false))).isEmpty());
        assertTrue(route(OWNER, List.of()).isEmpty());
    }

    @Test
    void aFakePlayerWithTheOwnersIdGetsNothing() {
        assertTrue(route(OWNER, List.of(new Viewer(OWNER, true))).isEmpty());
    }
}
