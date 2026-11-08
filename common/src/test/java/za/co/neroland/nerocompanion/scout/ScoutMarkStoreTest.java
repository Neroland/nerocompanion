package za.co.neroland.nerocompanion.scout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/** Client marks live for their TTL (2 s), are replaced by each batch, and are capped. */
class ScoutMarkStoreTest {

    private static List<Payloads.Mark> marks(int n) {
        List<Payloads.Mark> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Payloads.Mark(new BlockPos(i, 0, 0), Payloads.Mark.ORE));
        }
        return list;
    }

    @Test
    void expiresAfterTheTtl() {
        ScoutMarkStore store = new ScoutMarkStore();
        store.receive(marks(3), ScoutMarkSet.TTL_TICKS, 100L);
        assertEquals(3, store.active(100L).size());
        assertEquals(3, store.active(139L).size());
        assertTrue(store.active(140L).isEmpty());
        assertTrue(store.active(141L).isEmpty());
    }

    @Test
    void aRefreshExtendsAndReplaces() {
        ScoutMarkStore store = new ScoutMarkStore();
        store.receive(marks(5), 40, 0L);
        store.receive(marks(2), 40, 30L);
        assertEquals(2, store.active(60L).size());
        assertTrue(store.active(70L).isEmpty());
        store.receive(List.of(), 40, 80L);
        assertTrue(store.active(81L).isEmpty());
    }

    @Test
    void oversizedBatchesAndTtlsAreClamped() {
        ScoutMarkStore store = new ScoutMarkStore();
        store.receive(marks(64), Integer.MAX_VALUE, 0L);
        assertEquals(Payloads.ScoutMarks.MAX_MARKS, store.active(1L).size());
        assertTrue(store.active(ScoutMarkStore.MAX_TTL_TICKS).isEmpty());
        store.receive(marks(4), -5, 10L);
        assertTrue(store.active(10L).isEmpty());
    }

    @Test
    void thePayloadItselfIsCapped() {
        assertEquals(32, new Payloads.ScoutMarks(marks(50), 40).marks().size());
    }
}
