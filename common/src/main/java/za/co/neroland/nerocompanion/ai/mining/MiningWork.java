package za.co.neroland.nerocompanion.ai.mining;

import java.util.EnumSet;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.item.RemoteBlockActions;
import za.co.neroland.nerocompanion.job.JobCodecs;
import za.co.neroland.nerocompanion.mining.MineJob;
import za.co.neroland.nerocompanion.mining.MiningCorners;

/** WP-B mining registration: the {@code mine_area} work goal, the {@code mine_job} codec and the Remote corners. */
public final class MiningWork {

    public static final Identifier MINE_AREA = Identifier.fromNamespaceAndPath("nerocompanion", "mine_area");
    public static final Identifier MINE_JOB = MineJob.TYPE;

    private static boolean registered;

    private MiningWork() {
    }

    /** Common init (once, before type files are read). Idempotent so unit tests may call it too. */
    public static synchronized void register() {
        if (registered || WorkGoals.get(MINE_AREA).isPresent()) {
            registered = true;
            return;
        }
        registered = true;
        WorkGoals.register(MINE_AREA, WorkGoalFactory.of(EnumSet.of(CompanionMode.MINE), MineJobGoal::new));
        JobCodecs.register(MINE_JOB, MineJob.CODEC);
        RemoteBlockActions.register(MiningCorners::onSneakUse);
    }
}
