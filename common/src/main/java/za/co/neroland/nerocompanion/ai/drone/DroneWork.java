package za.co.neroland.nerocompanion.ai.drone;

import java.util.EnumSet;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.ai.work.WorkGoalFactory;
import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.job.JobCodecs;

/**
 * Registers the drones' work goals and the lamp job codec (WP-A). Called once from common init, before any companion
 * type file is read. Ids: {@code nerocompanion:drone_*} and {@code nerocompanion:scout_*}.
 */
public final class DroneWork {

    /** Personal Drone carry-lite: FOLLOW (overflow buffer) and CARRY (collect everything in reach). */
    public static final Identifier CARRY_LITE = id("drone_carry_lite");
    /** Lamp Chip light, any awake mode. */
    public static final Identifier LAMP = id("drone_lamp");
    /** Scout Drone: fly ahead and mark ores/hostiles, SCOUT only. */
    public static final Identifier SCOUT_AHEAD = id("scout_ahead");
    /** Scout Drone: danger behind the owner, FOLLOW, STAY and SCOUT. */
    public static final Identifier SCOUT_DANGER_ALERT = id("scout_danger_alert");

    /** Chatter key of the danger chirp (a lang key; the bubble shows it, the client flashes the drone). */
    public static final String CHATTER_DANGER = "chatter.nerocompanion.scout.danger";

    private DroneWork() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion", path);
    }

    public static void register() {
        WorkGoals.register(CARRY_LITE, WorkGoalFactory.of(EnumSet.of(CompanionMode.FOLLOW, CompanionMode.CARRY),
                (companion, type) -> new CarryLiteGoal(companion)));
        WorkGoals.register(LAMP, WorkGoalFactory.of(EnumSet.complementOf(EnumSet.of(CompanionMode.SLEEP)),
                (companion, type) -> lampGoal(companion)));
        WorkGoals.register(SCOUT_AHEAD, WorkGoalFactory.of(EnumSet.of(CompanionMode.SCOUT),
                (companion, type) -> new ScoutAheadGoal(companion)));
        WorkGoals.register(SCOUT_DANGER_ALERT, WorkGoalFactory.of(
                EnumSet.of(CompanionMode.FOLLOW, CompanionMode.STAY, CompanionMode.SCOUT),
                (companion, type) -> new DangerAlertGoal(companion)));
        JobCodecs.register(LampJob.TYPE, LampJob.CODEC);
    }

    /** The lamp goal for a class that owns a lamp; an inert goal for any other chassis a pack lists it on. */
    static Goal lampGoal(CompanionEntity companion) {
        if (companion instanceof LampCarrier carrier) {
            return new LampGoal(companion, carrier.lamp());
        }
        return new Goal() {
            @Override
            public boolean canUse() {
                return false;
            }
        };
    }
}
