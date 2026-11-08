package za.co.neroland.nerocompanion.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/** Two-corner picks: per owner, per bot, per dimension, and they expire. */
class CornerPicksTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

    @Test
    void twoPicksOnTheSameBotCompleteAPair() {
        CornerPicks picks = new CornerPicks();
        UUID owner = UUID.randomUUID();
        UUID bot = UUID.randomUUID();
        assertFalse(picks.pick(owner, bot, OVERWORLD, new BlockPos(1, 2, 3), 100L).completed());
        CornerPicks.Pick second = picks.pick(owner, bot, OVERWORLD, new BlockPos(4, 5, 6), 150L);
        assertTrue(second.completed());
        assertEquals(new BlockPos(1, 2, 3), second.first().orElseThrow());
        assertTrue(picks.pendingFor(owner).isEmpty());
    }

    @Test
    void anotherPlayersPickNeverCompletesMine() {
        CornerPicks picks = new CornerPicks();
        UUID bot = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        picks.pick(me, bot, OVERWORLD, BlockPos.ZERO, 0L);
        assertFalse(picks.pick(UUID.randomUUID(), bot, OVERWORLD, BlockPos.ZERO, 1L).completed());
        assertTrue(picks.pendingFor(me).isPresent());
    }

    @Test
    void aDifferentBotDimensionOrAnExpiredPickStartsOver() {
        CornerPicks picks = new CornerPicks();
        UUID owner = UUID.randomUUID();
        UUID bot = UUID.randomUUID();
        picks.pick(owner, bot, OVERWORLD, BlockPos.ZERO, 0L);
        assertFalse(picks.pick(owner, UUID.randomUUID(), OVERWORLD, BlockPos.ZERO, 1L).completed());
        picks.pick(owner, bot, OVERWORLD, BlockPos.ZERO, 10L);
        assertFalse(picks.pick(owner, bot, NETHER, BlockPos.ZERO, 11L).completed());
        picks.pick(owner, bot, OVERWORLD, BlockPos.ZERO, 20L);
        assertFalse(picks.pick(owner, bot, OVERWORLD, BlockPos.ZERO, 20L + CornerPicks.EXPIRY_TICKS + 1L)
                .completed());
    }
}
