package za.co.neroland.nerocompanion.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * The creative gallery's display companions for this server run, by entity UUID, with the pose each shows. A
 * display companion that loads without an entry here (its gallery was cleared while its chunk was unloaded, or the
 * server restarted) removes itself. Display companions never enter the Recovery Ledger and never count toward caps.
 */
public final class GalleryDisplays {

    private static final Map<UUID, GalleryPose> POSES = new ConcurrentHashMap<>();

    private GalleryDisplays() {
    }

    public static void put(UUID entityId, GalleryPose pose) {
        POSES.put(entityId, pose);
    }

    @Nullable
    public static GalleryPose poseFor(UUID entityId) {
        return POSES.get(entityId);
    }

    public static void remove(UUID entityId) {
        POSES.remove(entityId);
    }

    /** Server stopped. */
    public static void serverStopped() {
        POSES.clear();
    }
}
