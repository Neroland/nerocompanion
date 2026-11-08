package za.co.neroland.nerocompanion.mining;

import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.BlockPos;

import za.co.neroland.nerocompanion.authority.Decision;

/**
 * The mining bot's rules, pure (plain-JVM tests): what to do with the block under the cursor, how long a block
 * takes, and which owner-authority denials skip a block, pause the job or stop it.
 *
 * <p>The bot breaks like a player holding an <b>iron drill</b>: the best of an iron pickaxe, shovel and axe for
 * speed, and an iron pickaxe's harvest level for what drops. Blocks the drill cannot harvest (obsidian, ancient
 * debris...) are skipped, never broken for nothing. A Speed chip multiplies the break speed (+10 % per tier).
 */
public final class MineRules {

    /** FE paid per broken block. */
    public static final long FE_PER_BLOCK = 120L;

    /** Most cursor positions looked at in one scan (air is skipped cheaply; bounded so a scan is never a sweep). */
    public static final int SCAN_BUDGET = 256;

    /** Denials that mean "not this block": the cursor moves on. Everything else (claims, spawn...) stops the job. */
    private static final Set<String> SKIP_REASONS = Set.of(
            Decision.deny("unmineable").reasonKey(),
            Decision.deny("unbreakable").reasonKey(),
            Decision.deny("block_entity").reasonKey());

    /** Denials that will clear by themselves (owner logs back in, walks back): the job waits. */
    private static final Set<String> PAUSE_REASONS = Set.of(
            Decision.deny("owner_offline").reasonKey(),
            Decision.deny("other_dimension").reasonKey());

    private MineRules() {
    }

    /** What the bot knows about one block before asking for authority. Built by the game side. */
    public record Survey(boolean air, boolean unmineable, boolean filteredOut, boolean harvestable,
            boolean nearFluid) {

        public static final Survey AIR = new Survey(true, false, false, true, false);

        public static Survey solid() {
            return new Survey(false, false, false, true, false);
        }
    }

    /** How the job reacts to an authority answer for one block. */
    public enum Reaction {
        BREAK,
        SKIP,
        PAUSE,
        STOP
    }

    public static Reaction react(Decision decision) {
        if (decision.allowed()) {
            return Reaction.BREAK;
        }
        if (SKIP_REASONS.contains(decision.reasonKey())) {
            return Reaction.SKIP;
        }
        if (PAUSE_REASONS.contains(decision.reasonKey())) {
            return Reaction.PAUSE;
        }
        return Reaction.STOP;
    }

    /** The outcome of one {@link #scan}. */
    public enum Kind {
        /** {@code job.current()} is the next block to break. */
        TARGET,
        /** Every block has been visited. */
        DONE,
        /** Scan budget spent on air/skips; look again next scan. */
        CONTINUE,
        /** Authority said "not now" (owner offline / elsewhere); job kept. */
        PAUSE,
        /** Authority said no for a reason that will not clear (claim, spawn protection...); the job ends. */
        STOP
    }

    /** A scan result: the advanced job, and for PAUSE/STOP the denial's lang key. */
    public record Step(Kind kind, MineJob job, String reasonKey) {
    }

    /**
     * Advances the cursor past air, unmineable, filtered, unharvestable and fluid-adjacent (lava or water) blocks (the tag is
     * checked <em>before</em> authority, so an unmineable block never even reaches the chain), then asks
     * {@code authority} for the first candidate. Authority is asked per block, every time. Pure.
     */
    public static Step scan(MineJob job, Function<BlockPos, Survey> survey, Function<BlockPos, Decision> authority,
            int budget) {
        MineJob current = job;
        for (int looked = 0; looked < budget; looked++) {
            if (current.finished()) {
                return new Step(Kind.DONE, current, "");
            }
            BlockPos pos = current.current();
            Survey facts = survey.apply(pos);
            if (facts.air()) {
                current = current.pass();
                continue;
            }
            if (facts.unmineable() || facts.filteredOut() || !facts.harvestable() || facts.nearFluid()) {
                current = current.skip();
                continue;
            }
            Decision decision = authority.apply(pos);
            switch (react(decision)) {
                case BREAK:
                    return new Step(Kind.TARGET, current, "");
                case SKIP:
                    current = current.skip();
                    continue;
                case PAUSE:
                    return new Step(Kind.PAUSE, current, decision.reasonKey());
                default:
                    return new Step(Kind.STOP, current, decision.reasonKey());
            }
        }
        return new Step(current.finished() ? Kind.DONE : Kind.CONTINUE, current, "");
    }

    /**
     * Ticks to break a block like a player: progress per tick = speed / hardness / (30 if the tool harvests it,
     * else 100). Instant blocks (hardness 0) take one tick. Never less than 1, capped at 1200 (a minute).
     */
    public static int breakTicks(float hardness, float toolSpeed, boolean harvests, double speedMultiplier) {
        if (hardness <= 0.0F) {
            return 1;
        }
        double perTick = Math.max(0.01D, toolSpeed) * Math.max(0.1D, speedMultiplier) / hardness
                / (harvests ? 30.0D : 100.0D);
        return (int) Math.max(1L, Math.min(1200L, (long) Math.ceil(1.0D / perTick - 1.0E-9D)));
    }

    /**
     * Work needs the owner online, in the bot's level and within {@code sleepDistance} blocks (default 64): a bot is
     * never a way to dig or haul somewhere its owner is not. Beyond it the job pauses and resumes on return.
     */
    public static boolean ownerInRange(boolean ownerInLevel, double distanceSq, int sleepDistance) {
        return ownerInLevel && distanceSq <= (double) sleepDistance * sleepDistance;
    }

    /** The crack-overlay stage (0-9) for {@code elapsed} of {@code total} ticks. */
    public static int crackStage(int elapsed, int total) {
        if (total <= 0) {
            return 9;
        }
        return Math.max(0, Math.min(9, (int) (10.0D * elapsed / total) - 1));
    }
}
