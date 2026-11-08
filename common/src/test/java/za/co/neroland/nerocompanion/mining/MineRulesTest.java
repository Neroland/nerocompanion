package za.co.neroland.nerocompanion.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.Decision;

/** Mining rules: per-block authority, the unmineable tag, the ore filter, and break timing. */
class MineRulesTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    /** A 1 x 1 x 4 column: cursor 0..3 from the top. */
    private static MineJob column() {
        return MineJob.start(OVERWORLD, MineArea.of(new BlockPos(0, 3, 0), new BlockPos(0, 0, 0), 9));
    }

    @Test
    void aPerBlockDenialStopsTheJobAtThatBlock() {
        MineJob job = column();
        BlockPos claimed = job.area().at(1);
        List<BlockPos> asked = new ArrayList<>();
        Function<BlockPos, Decision> authority = pos -> {
            asked.add(pos);
            return pos.equals(claimed) ? Decision.deny("provider") : Decision.ALLOW;
        };
        // Block 0 is allowed: it is the target; nothing past it is asked.
        MineRules.Step first = MineRules.scan(job, pos -> MineRules.Survey.solid(), authority, 64);
        assertEquals(MineRules.Kind.TARGET, first.kind());
        assertEquals(List.of(job.area().at(0)), asked);
        // The bot broke block 0; the next scan reaches the claimed block and stops there, cleanly.
        MineRules.Step second = MineRules.scan(first.job().broke(), pos -> MineRules.Survey.solid(), authority, 64);
        assertEquals(MineRules.Kind.STOP, second.kind());
        assertEquals("authority.nerocompanion.provider", second.reasonKey());
        assertEquals(claimed, second.job().current());
        assertEquals(1, second.job().mined());
        assertEquals(List.of(job.area().at(0), claimed), asked);
    }

    @Test
    void ownerOfflineOrElsewherePausesInsteadOfStopping() {
        assertEquals(MineRules.Reaction.PAUSE, MineRules.react(Decision.deny("owner_offline")));
        assertEquals(MineRules.Reaction.PAUSE, MineRules.react(Decision.deny("other_dimension")));
        MineRules.Step step = MineRules.scan(column(), pos -> MineRules.Survey.solid(),
                pos -> Decision.deny("owner_offline"), 64);
        assertEquals(MineRules.Kind.PAUSE, step.kind());
        assertEquals(0, step.job().cursor());
    }

    @Test
    void claimsSpawnAndBuildDenialsStop() {
        for (String reason : List.of("spawn_protection", "cannot_build", "no_interact", "provider_error", "gate",
                "world_border", "mining_disabled")) {
            assertEquals(MineRules.Reaction.STOP, MineRules.react(Decision.deny(reason)), reason);
        }
    }

    @Test
    void unmineableBlocksAreSkippedWithoutAskingAuthority() {
        MineJob job = column();
        BlockPos bedrock = job.area().at(0);
        List<BlockPos> asked = new ArrayList<>();
        MineRules.Step step = MineRules.scan(job,
                pos -> pos.equals(bedrock) ? new MineRules.Survey(false, true, false, true, false)
                        : MineRules.Survey.solid(),
                pos -> {
                    asked.add(pos);
                    return Decision.ALLOW;
                }, 64);
        assertEquals(MineRules.Kind.TARGET, step.kind());
        assertEquals(job.area().at(1), step.job().current());
        assertEquals(1, step.job().skipped());
        assertFalse(asked.contains(bedrock));
        // And if the tag check were bypassed, the chain's own unmineable denial still only skips.
        assertEquals(MineRules.Reaction.SKIP, MineRules.react(Decision.deny("unmineable")));
        assertEquals(MineRules.Reaction.SKIP, MineRules.react(Decision.deny("unbreakable")));
        assertEquals(MineRules.Reaction.SKIP, MineRules.react(Decision.deny("block_entity")));
    }

    @Test
    void theOreFilterAirLavaAndUnharvestableBlocksAreSkipped() {
        MineJob job = column();
        Map<BlockPos, MineRules.Survey> world = Map.of(
                job.area().at(0), MineRules.Survey.AIR,
                job.area().at(1), new MineRules.Survey(false, false, true, true, false),
                job.area().at(2), new MineRules.Survey(false, false, false, false, false),
                job.area().at(3), new MineRules.Survey(false, false, false, true, true));
        MineRules.Step step = MineRules.scan(job, world::get, pos -> Decision.ALLOW, 64);
        assertEquals(MineRules.Kind.DONE, step.kind());
        assertEquals(3, step.job().skipped());
        assertEquals(0, step.job().mined());
    }

    @Test
    void theScanBudgetBoundsOnePass() {
        MineJob job = MineJob.start(OVERWORLD, MineArea.of(BlockPos.ZERO, new BlockPos(8, 8, 8), 9));
        MineRules.Step step = MineRules.scan(job, pos -> MineRules.Survey.AIR, pos -> Decision.ALLOW, 100);
        assertEquals(MineRules.Kind.CONTINUE, step.kind());
        assertEquals(100, step.job().cursor());
    }

    @Test
    void workNeedsTheOwnerWithinSleepDistanceInTheSameLevel() {
        assertTrue(MineRules.ownerInRange(true, 10.0D * 10.0D, 64));
        assertTrue(MineRules.ownerInRange(true, 64.0D * 64.0D, 64));
        assertFalse(MineRules.ownerInRange(true, 65.0D * 65.0D, 64));
        assertFalse(MineRules.ownerInRange(false, 0.0D, 64));
    }

    @Test
    void breakTimingFollowsThePlayerFormula() {
        assertEquals(8, MineRules.breakTicks(1.5F, 6.0F, true, 1.0D)); // stone, iron pickaxe
        assertEquals(15, MineRules.breakTicks(0.5F, 1.0F, true, 1.0D)); // dirt, bare head
        assertEquals(1, MineRules.breakTicks(0.0F, 1.0F, true, 1.0D)); // instant
        assertTrue(MineRules.breakTicks(1.5F, 6.0F, true, 1.3D) < 8); // Speed III
        assertTrue(MineRules.breakTicks(1.5F, 6.0F, false, 1.0D) > 8); // wrong tool is slower
        assertEquals(9, MineRules.crackStage(100, 100));
        assertEquals(0, MineRules.crackStage(0, 100));
        assertEquals(120L, MineRules.FE_PER_BLOCK);
    }
}
