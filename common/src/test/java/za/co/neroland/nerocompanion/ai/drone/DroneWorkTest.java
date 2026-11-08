package za.co.neroland.nerocompanion.ai.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.ai.work.WorkGoals;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.job.JobCodecs;

/** WP-A registration, the mode selection of each drone goal, and the lamp job round trip. */
class DroneWorkTest {

    private static synchronized void ensureRegistered() {
        if (WorkGoals.get(DroneWork.LAMP).isEmpty() && !JobCodecs.isRegistered(LampJob.TYPE)) {
            DroneWork.register();
        }
    }

    @Test
    void goalsAreKnownWithTheirModes() {
        ensureRegistered();
        assertTrue(WorkGoals.known().containsAll(List.of(DroneWork.CARRY_LITE, DroneWork.LAMP, DroneWork.SCOUT_AHEAD,
                DroneWork.SCOUT_DANGER_ALERT)));
        assertTrue(JobCodecs.isRegistered(LampJob.TYPE));
        List<Identifier> scout = List.of(DroneWork.SCOUT_AHEAD, DroneWork.SCOUT_DANGER_ALERT, DroneWork.LAMP);
        assertEquals(scout, WorkGoals.activeFor(scout, CompanionMode.SCOUT));
        assertEquals(List.of(DroneWork.SCOUT_DANGER_ALERT, DroneWork.LAMP),
                WorkGoals.activeFor(scout, CompanionMode.FOLLOW));
        assertEquals(List.of(), WorkGoals.activeFor(scout, CompanionMode.SLEEP));
        List<Identifier> personal = List.of(DroneWork.CARRY_LITE, DroneWork.LAMP);
        assertEquals(personal, WorkGoals.activeFor(personal, CompanionMode.CARRY));
        assertEquals(List.of(DroneWork.LAMP), WorkGoals.activeFor(personal, CompanionMode.STAY));
    }

    @Test
    void lampJobRoundTripsAndIsCapped() {
        LampJob job = new LampJob(List.of(
                new LampJob.Spot(Identifier.fromNamespaceAndPath("minecraft", "overworld"), new BlockPos(1, -60, 3)),
                new LampJob.Spot(Identifier.fromNamespaceAndPath("minecraft", "the_nether"), new BlockPos(0, 64, 0))));
        JsonElement json = LampJob.CODEC.encodeStart(JsonOps.INSTANCE, job).getOrThrow();
        assertEquals(job, LampJob.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        LampJob many = new LampJob(List.of(job.spots().get(0), job.spots().get(1), job.spots().get(0),
                job.spots().get(1)));
        assertEquals(LampJob.MAX_SPOTS, many.spots().size());
        assertEquals(LampJob.TYPE, job.jobType());
    }
}
